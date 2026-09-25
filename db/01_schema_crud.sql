-- =====================================================================
-- 01. CRUD용 스키마 보강 (food_recommend_back)
-- 실행 순서: 01_schema_crud.sql → 02_rls_policies.sql
-- Supabase SQL Editor에서 실행. 여러 번 실행해도 안전(멱등). 컬럼 "추가"만 하고
-- 기존 데이터는 건드리지 않습니다.
--
-- 삭제 정책: 전부 soft delete (row를 지우지 않고 deleted_at 등을 채움)
--   - 규정위반 삭제는 "누가/왜" 지웠는지 기록이 남아야 하고
--   - 다른 테이블이 참조 중인 row(메뉴 ← 리뷰 등)를 지우면 참조가 깨지기 때문
--
--   delete_reason 값
--     self       : 작성자 본인이 삭제
--     violation  : 회사관리자/관리자가 규정위반으로 삭제 (delete_note에 사유 필수)
--     withdrawal : 작성자가 회원 탈퇴해서 같이 삭제
--     cascade    : 상위 항목(레스토랑)이 삭제돼서 같이 삭제
--     admin      : 관리자 정리(회사 삭제 등)
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- 1. soft delete / 수정시각 컬럼
-- ---------------------------------------------------------------------
alter table restaurants
    add column if not exists updated_at    timestamptz,
    add column if not exists deleted_at    timestamptz,
    add column if not exists deleted_by    text,
    add column if not exists delete_reason text,
    add column if not exists delete_note   text;

alter table menus
    add column if not exists updated_at    timestamptz,
    add column if not exists deleted_at    timestamptz,
    add column if not exists deleted_by    text,
    add column if not exists delete_reason text,
    add column if not exists delete_note   text;

alter table reviews
    add column if not exists updated_at    timestamptz,
    add column if not exists deleted_at    timestamptz,
    add column if not exists deleted_by    text,
    add column if not exists delete_reason text,
    add column if not exists delete_note   text;

alter table companies
    add column if not exists updated_at timestamptz,
    add column if not exists deleted_at timestamptz,
    add column if not exists deleted_by text;

do $$
declare t text;
begin
    foreach t in array array['restaurants', 'menus', 'reviews'] loop
        begin
            execute format(
                'alter table %I add constraint %I check (delete_reason is null or delete_reason in (''self'',''violation'',''withdrawal'',''cascade'',''admin''))',
                t, t || '_delete_reason_chk');
        exception when duplicate_object then null;
        end;
    end loop;
end $$;

-- 초대코드는 회사(지점)를 식별하는 값이라 중복되면 안 됨
-- (이미 중복 데이터가 있으면 여기서 실패합니다 — 그 경우 중복을 정리한 뒤 다시 실행)
create unique index if not exists companies_invite_code_uq on companies (invite_code);

-- 목록 조회용 (삭제 안 된 것만)
create index if not exists restaurants_company_alive_idx on restaurants (company_id) where deleted_at is null;
create index if not exists menus_company_alive_idx       on menus (company_id, approval_status) where deleted_at is null;
create index if not exists menus_restaurant_idx          on menus (restaurant_id);
create index if not exists reviews_restaurant_alive_idx  on reviews (restaurant_id) where deleted_at is null;
create index if not exists reviews_user_idx              on reviews (user_id);
create index if not exists review_items_menu_idx         on review_items (menu_id);
create index if not exists review_items_review_idx       on review_items (review_id);

-- ---------------------------------------------------------------------
-- 2. 방어용 트리거 (앱 코드가 실수해도 DB가 막아줌)
--    current_app_role()이 비어 있는 경우(= SQL Editor에서 직접 작업)는 통과시킵니다.
-- ---------------------------------------------------------------------

-- 2-1. 일반 사용자는 메뉴를 스스로 승인/반려할 수 없음
--      (자기 메뉴를 수정하면 앱이 pending으로 되돌리는 건 허용)
create or replace function guard_menu_approval() returns trigger
    language plpgsql
as
$$
begin
    if new.approval_status is distinct from old.approval_status
       and new.approval_status <> 'pending'
       and current_app_role() is not null
       and current_app_role() not in ('company_admin', 'admin') then
        raise exception '메뉴 승인 상태는 회사관리자/관리자만 변경할 수 있습니다.' using errcode = '42501';
    end if;
    return new;
end;
$$;

drop trigger if exists menus_guard_approval on menus;
create trigger menus_guard_approval before update on menus
    for each row execute function guard_menu_approval();

-- 2-2. 역할/소속 회사는 관리자만 변경 (본인 소속 변경은 change_my_company() 경유만 허용)
create or replace function guard_user_privileges() returns trigger
    language plpgsql
as
$$
begin
    if current_app_role() is null or current_app_role() = 'admin' then
        return new;
    end if;
    if new.role is distinct from old.role then
        raise exception '역할은 관리자만 변경할 수 있습니다.' using errcode = '42501';
    end if;
    if new.company_id is distinct from old.company_id
       and coalesce(current_setting('app.allow_company_change', true), '') <> 'on' then
        raise exception '소속 회사는 초대코드 변경으로만 바꿀 수 있습니다.' using errcode = '42501';
    end if;
    return new;
end;
$$;

drop trigger if exists users_guard_privileges on users;
create trigger users_guard_privileges before update on users
    for each row execute function guard_user_privileges();

-- ---------------------------------------------------------------------
-- 3. 본인 계정 관련 함수 (SECURITY DEFINER — RLS상 본인 row UPDATE 권한을 따로 주지 않기 위함)
-- ---------------------------------------------------------------------

-- 3-1. 회원 탈퇴: 상태 withdrawn + 내가 쓴 리뷰를 'withdrawal' 사유로 삭제
--      (내가 등록한 레스토랑/메뉴는 회사 공용 정보라 남겨둠)
create or replace function withdraw_current_user() returns void
    language plpgsql
    security definer
    set search_path = public
as
$$
declare
    v_uid text := current_app_user_id();
begin
    if v_uid is null then
        raise exception '로그인 컨텍스트가 없습니다.' using errcode = '42501';
    end if;

    update users
       set status = 'withdrawn', status_reason = '본인 탈퇴', status_changed_at = now()
     where id = v_uid;

    update reviews
       set deleted_at = now(), deleted_by = v_uid, delete_reason = 'withdrawal'
     where user_id = v_uid and deleted_at is null;
end;
$$;

-- 3-2. 소속 변경(사내 이동 등): 새 초대코드로 회사를 바꿈. 일반 사용자만 가능.
create or replace function change_my_company(p_invite_code text) returns uuid
    language plpgsql
    security definer
    set search_path = public
as
$$
declare
    v_uid     text := current_app_user_id();
    v_company uuid;
begin
    if v_uid is null then
        raise exception '로그인 컨텍스트가 없습니다.' using errcode = '42501';
    end if;

    select id into v_company from companies where invite_code = p_invite_code and deleted_at is null;
    if v_company is null then
        raise exception '유효하지 않은 초대코드입니다.' using errcode = 'P0002';
    end if;

    perform set_config('app.allow_company_change', 'on', true);
    update users set company_id = v_company
     where id = v_uid and role = 'general_user' and status = 'active';
    if not found then
        raise exception '일반 사용자만 초대코드를 변경할 수 있습니다.' using errcode = '42501';
    end if;
    perform set_config('app.allow_company_change', '', true);
    return v_company;
end;
$$;

revoke all on function withdraw_current_user() from public;
revoke all on function change_my_company(text) from public;
do $$
begin
    execute 'revoke all on function withdraw_current_user() from anon, authenticated';
    execute 'revoke all on function change_my_company(text) from anon, authenticated';
exception when undefined_object then null;
end $$;
grant execute on function withdraw_current_user() to app_backend;
grant execute on function change_my_company(text) to app_backend;

commit;
