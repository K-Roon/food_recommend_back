package com.kroon.food_recommend_back.service;

import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Supabase RLS용 Postgres 세션 컨텍스트(app.current_user_id / app.current_company_id /
 * app.current_role)를 요청마다 부트스트랩합니다.
 *
 * 2단계로 나뉘는 이유:
 * 1단계 - uid만 알고 company_id/role은 모르는 상태로 set_app_context 호출
 *         → users 테이블의 "본인 row는 조회 가능(id = current_app_user_id())" RLS 정책
 *           덕분에, 이 상태로도 JPA로 본인 정보 조회가 가능함
 * 2단계 - 방금 조회한 company_id/role로 다시 set_app_context 호출
 *         → 이후 실행되는 다른 모든 쿼리에 회사 스코프 RLS가 정상 적용됨
 *
 * ⚠️ 트랜잭션 범위 주의:
 * DB의 set_app_context()는 set_config(..., is_local = true)로 값을 넣습니다.
 * 즉 이 값은 "현재 트랜잭션"이 끝나면 사라집니다. 그래서 bootstrap()을 호출하는
 * 서비스 메서드는 반드시 @Transactional 안에서 호출해야 하고(이 메서드는 기본
 * 전파 REQUIRED라 바깥 트랜잭션에 합류함), 그 뒤의 조회/저장도 같은 트랜잭션에서
 * 실행돼야 RLS가 올바르게 걸립니다. 지금은 백엔드가 postgres 슈퍼유저로 접속해서
 * RLS가 우회되고 있어 티가 안 나지만, app_backend 롤로 바꾸는 순간 이게 안 지켜진
 * 쿼리는 전부 0건/거부가 됩니다. 그래서 각 서비스 클래스에 클래스 레벨
 * @Transactional(readOnly = true)을 걸어두었습니다.
 *
 * 또한 정지(suspended)/밴(banned)/제명(deleted)/탈퇴(withdrawn) 상태인 계정은 여기서 403으로
 * 막습니다 — Firebase 계정을 disable해도 이미 발급된 ID 토큰은 최대 1시간 유효하기 때문.
 */
@Service
@RequiredArgsConstructor
public class RlsContextService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    @Transactional
    public User bootstrap(String uid) {
        setContext(uid, null, null);

        User user = userRepository.findById(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "가입 정보가 없는 계정입니다. 회원가입을 먼저 완료해 주세요."));

        if (user.getStatus() != User.Status.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, switch (user.getStatus()) {
                case suspended -> "이용이 정지된 계정입니다.";
                case banned -> "이용이 영구 제한된 계정입니다.";
                case deleted -> "회사에서 제외된 계정입니다.";
                case withdrawn -> "탈퇴한 계정입니다.";
                default -> "사용할 수 없는 계정입니다.";
            });
        }

        setContext(uid,
                user.getCompanyId() != null ? user.getCompanyId().toString() : null,
                user.getRole() != null ? user.getRole().name() : null);

        return user;
    }

    /** 회원가입처럼 users row가 아직 없을 때 직접 컨텍스트를 지정해야 하는 경우용. */
    @Transactional
    public void setContext(String uid, String companyId, String role) {
        jdbcTemplate.queryForObject("select set_app_context(?, ?::uuid, ?)", Object.class, uid, companyId, role);
    }
}
