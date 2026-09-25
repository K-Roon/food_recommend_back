-- =====================================================================
-- 02. RLS 정책 일괄 적용 (food_recommend_back)
-- 실행 순서: 01_schema_crud.sql → 02_rls_policies.sql
-- Supabase SQL Editor에 통째로 붙여서 실행하면 됩니다. 여러 번 실행해도 안전(멱등).
--
-- 전제: 백엔드는 요청마다 set_app_context(uid, company_id, role)을 호출하고,
--       current_app_user_id() / current_app_company_id() / current_app_role()로 읽습니다.
--       (이미 DB에 있는 함수들 — 여기서는 건드리지 않음)
--
-- 이 파일이 하는 일
--   1. 회원가입용 SECURITY DEFINER 함수 company_id_by_invite_code() 생성
--      → 로그인 전(컨텍스트 없음)에도 초대코드로 회사 id만 조회 가능
--   2. app_backend 롤에 테이블 권한 부여
--   3. companies / users / restaurants / menus / menu_option_groups / menu_options 정책
--      + reviews / review_items (subscriptions는 광고 제거 기능 활성화 때 추가)
--
-- 정책은 전부 "to app_backend" 전용입니다. Supabase의 anon/authenticated(PostgREST
-- 공개 API)에는 어떤 정책도 주지 않으므로, 앱 밖에서 Supabase API로 직접 긁어가는
-- 경로는 전부 막힌 상태가 유지됩니다.
--
-- ⚠️ 백엔드가 아직 postgres(슈퍼유저)로 접속 중이면 RLS가 우회되므로 이 정책들은
--    "적용은 되지만 효과는 없는" 상태입니다. 마지막 단계에서 접속 계정을
--    app_backend로 바꾸면 그때부터 실제로 걸립니다 (파일 맨 아래 안내 참고).
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- 1. 회원가입용: 초대코드 → company_id
-- ---------------------------------------------------------------------
create or replace function company_id_by_invite_code(p_code text) returns uuid
    language sql
    stable
    security definer
    set search_path = public
as
$$
  select id from companies where invite_code = p_code and deleted_at is null limit 1;
$$;

revoke all on function company_id_by_invite_code(text) from public;
revoke all on function company_id_by_invite_code(text) from anon, authenticated;
grant execute on function company_id_by_invite_code(text) to app_backend;

-- ---------------------------------------------------------------------
-- 2. app_backend 권한 (DELETE는 주지 않음 — 삭제 기능은 전부 soft delete)
-- ---------------------------------------------------------------------
grant usage on schema public to app_backend;
-- 레스토랑/메뉴/리뷰/회사 삭제는 soft delete(UPDATE)라 DELETE 권한이 필요 없음.
-- 옵션 그룹/옵션/리뷰 항목은 부모에 딸린 설정값이라 실제 DELETE (수정 시 교체).
grant select, insert, update on companies, users, restaurants, menus, menu_option_groups, menu_options, reviews to app_backend;
grant select, insert on review_items to app_backend;
grant delete on menu_option_groups, menu_options, review_items to app_backend;
grant execute on function current_app_user_id(), current_app_company_id(), current_app_role() to app_backend;

alter table companies          enable row level security;
alter table users              enable row level security;
alter table restaurants        enable row level security;
alter table menus              enable row level security;
alter table menu_option_groups enable row level security;
alter table menu_options       enable row level security;
alter table reviews            enable row level security;
alter table review_items       enable row level security;

-- ---------------------------------------------------------------------
-- 3-1. companies — 내 회사만 보기, 설정 변경은 회사관리자/관리자
-- ---------------------------------------------------------------------
drop policy if exists companies_select on companies;
create policy companies_select on companies for select to app_backend
    using (id = current_app_company_id() or current_app_role() = 'admin');

drop policy if exists companies_insert on companies;
create policy companies_insert on companies for insert to app_backend
    with check (current_app_role() = 'admin');

drop policy if exists companies_update on companies;
create policy companies_update on companies for update to app_backend
    using (current_app_role() = 'admin'
           or (current_app_role() = 'company_admin' and id = current_app_company_id()))
    with check (current_app_role() = 'admin'
           or (current_app_role() = 'company_admin' and id = current_app_company_id()));

-- ---------------------------------------------------------------------
-- 3-2. users
--   select: 본인 / 같은 회사(회사관리자) / 전체(관리자)
--   insert: 회원가입 — 본인 row를 general_user·active로만
--   update: 회사관리자는 자기 회사 general_user만, 관리자는 전체
-- ---------------------------------------------------------------------
drop policy if exists users_select_self on users;  -- 기존 정책을 아래 통합 정책으로 대체
drop policy if exists users_select on users;
create policy users_select on users for select to app_backend
    using (id = current_app_user_id()
           or current_app_role() = 'admin'
           or (current_app_role() = 'company_admin' and company_id = current_app_company_id()));

drop policy if exists users_insert_self on users;
create policy users_insert_self on users for insert to app_backend
    with check (id = current_app_user_id()
                and company_id = current_app_company_id()
                and role = 'general_user'
                and status = 'active');

drop policy if exists users_update on users;
create policy users_update on users for update to app_backend
    using (current_app_role() = 'admin'
           or (current_app_role() = 'company_admin' and company_id = current_app_company_id() and role = 'general_user'))
    with check (current_app_role() = 'admin'
           or (current_app_role() = 'company_admin' and company_id = current_app_company_id() and role = 'general_user'));

-- ---------------------------------------------------------------------
-- 3-3. restaurants — 회사 스코프
-- ---------------------------------------------------------------------
drop policy if exists restaurants_select on restaurants;
create policy restaurants_select on restaurants for select to app_backend
    using (company_id = current_app_company_id() or current_app_role() = 'admin');

drop policy if exists restaurants_insert on restaurants;
create policy restaurants_insert on restaurants for insert to app_backend
    with check (company_id = current_app_company_id() and created_by = current_app_user_id());

drop policy if exists restaurants_update on restaurants;
create policy restaurants_update on restaurants for update to app_backend
    using (current_app_role() = 'admin'
           or (company_id = current_app_company_id()
               and (current_app_role() = 'company_admin' or created_by = current_app_user_id())))
    with check (current_app_role() = 'admin'
           or (company_id = current_app_company_id()
               and (current_app_role() = 'company_admin' or created_by = current_app_user_id())));

-- ---------------------------------------------------------------------
-- 3-4. menus — 회사 스코프. 수정/삭제는 등록자 본인 또는 회사관리자/관리자.
--   승인/반려는 회사관리자/관리자만 — 01의 menus_guard_approval 트리거가 DB 레벨에서 강제
--   (pending/rejected 메뉴를 "등록자·승인자만 보기"는 앱 레이어(MenuService)에서 처리)
-- ---------------------------------------------------------------------
drop policy if exists menus_select on menus;
create policy menus_select on menus for select to app_backend
    using (company_id = current_app_company_id() or current_app_role() = 'admin');

drop policy if exists menus_insert on menus;
create policy menus_insert on menus for insert to app_backend
    with check (company_id = current_app_company_id() and created_by = current_app_user_id());

drop policy if exists menus_update on menus;
create policy menus_update on menus for update to app_backend
    using (current_app_role() = 'admin'
           or (company_id = current_app_company_id()
               and (current_app_role() = 'company_admin' or created_by = current_app_user_id())))
    with check (current_app_role() = 'admin'
           or (company_id = current_app_company_id()
               and (current_app_role() = 'company_admin' or created_by = current_app_user_id())));

-- ---------------------------------------------------------------------
-- 3-5. menu_option_groups / menu_options — 부모 메뉴를 볼 수 있으면 접근 가능
--   (서브쿼리의 menus 조회에도 menus RLS가 그대로 적용되므로 회사 스코프가 자동으로 따라옴)
-- ---------------------------------------------------------------------
drop policy if exists menu_option_groups_select on menu_option_groups;
create policy menu_option_groups_select on menu_option_groups for select to app_backend
    using (exists (select 1 from menus m where m.id = menu_id));

drop policy if exists menu_option_groups_insert on menu_option_groups;
create policy menu_option_groups_insert on menu_option_groups for insert to app_backend
    with check (exists (select 1 from menus m where m.id = menu_id));

-- 수정/삭제: 부모 메뉴를 수정할 수 있는 사람(등록자/회사관리자/관리자)만
drop policy if exists menu_option_groups_update on menu_option_groups;
create policy menu_option_groups_update on menu_option_groups for update to app_backend
    using (exists (select 1 from menus m where m.id = menu_id
                   and (m.created_by = current_app_user_id() or current_app_role() in ('company_admin', 'admin'))));

drop policy if exists menu_option_groups_delete on menu_option_groups;
create policy menu_option_groups_delete on menu_option_groups for delete to app_backend
    using (exists (select 1 from menus m where m.id = menu_id
                   and (m.created_by = current_app_user_id() or current_app_role() in ('company_admin', 'admin'))));

drop policy if exists menu_options_select on menu_options;
create policy menu_options_select on menu_options for select to app_backend
    using (exists (select 1 from menu_option_groups g where g.id = option_group_id));

drop policy if exists menu_options_insert on menu_options;
create policy menu_options_insert on menu_options for insert to app_backend
    with check (exists (select 1 from menu_option_groups g where g.id = option_group_id));

drop policy if exists menu_options_update on menu_options;
create policy menu_options_update on menu_options for update to app_backend
    using (exists (select 1 from menu_option_groups g join menus m on m.id = g.menu_id
                   where g.id = option_group_id
                     and (m.created_by = current_app_user_id() or current_app_role() in ('company_admin', 'admin'))));

drop policy if exists menu_options_delete on menu_options;
create policy menu_options_delete on menu_options for delete to app_backend
    using (exists (select 1 from menu_option_groups g join menus m on m.id = g.menu_id
                   where g.id = option_group_id
                     and (m.created_by = current_app_user_id() or current_app_role() in ('company_admin', 'admin'))));

-- ---------------------------------------------------------------------
-- 3-6. reviews / review_items — 레스토랑(=회사 스코프)을 볼 수 있으면 조회 가능
--   작성은 본인 명의로만, 리뷰 항목은 "내 리뷰"에 "보이는 메뉴"로만
-- ---------------------------------------------------------------------
drop policy if exists reviews_select on reviews;
create policy reviews_select on reviews for select to app_backend
    using (exists (select 1 from restaurants r where r.id = restaurant_id));

drop policy if exists reviews_insert on reviews;
create policy reviews_insert on reviews for insert to app_backend
    with check (user_id = current_app_user_id()
                and exists (select 1 from restaurants r where r.id = restaurant_id));

-- 수정·본인삭제는 작성자, 규정위반 삭제는 같은 회사 회사관리자/관리자
drop policy if exists reviews_update on reviews;
create policy reviews_update on reviews for update to app_backend
    using (current_app_role() = 'admin'
           or (exists (select 1 from restaurants r where r.id = restaurant_id)
               and (user_id = current_app_user_id() or current_app_role() = 'company_admin')))
    with check (current_app_role() = 'admin'
           or (exists (select 1 from restaurants r where r.id = restaurant_id)
               and (user_id = current_app_user_id() or current_app_role() = 'company_admin')));

drop policy if exists review_items_select on review_items;
create policy review_items_select on review_items for select to app_backend
    using (exists (select 1 from reviews rv where rv.id = review_id));

drop policy if exists review_items_insert on review_items;
create policy review_items_insert on review_items for insert to app_backend
    with check (exists (select 1 from reviews rv where rv.id = review_id and rv.user_id = current_app_user_id())
                and exists (select 1 from menus m where m.id = menu_id));

-- 리뷰 수정 시 메뉴 항목 교체용 — 본인 리뷰의 항목만
drop policy if exists review_items_delete on review_items;
create policy review_items_delete on review_items for delete to app_backend
    using (exists (select 1 from reviews rv where rv.id = review_id and rv.user_id = current_app_user_id()));

commit;

-- =====================================================================
-- (마지막 단계에서 한 번만) 백엔드 접속 계정을 app_backend로 전환
-- ---------------------------------------------------------------------
--   alter role app_backend with login password '<새 비밀번호>';
--
--   그리고 Cloud Run / application-local.yml 설정을:
--     DB_USERNAME = app_backend.cwzmgcggefjcajhimlpz     (Supabase pooler는 "롤.프로젝트ref" 형식)
--     DB_PASSWORD = <새 비밀번호>   (Secret Manager의 db-password 새 버전으로)
--   으로 바꾸면 됩니다. 문제 생기면 원래 postgres 계정으로 되돌리면 즉시 복구됩니다.
-- =====================================================================
