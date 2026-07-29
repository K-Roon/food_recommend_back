package com.kroon.food_recommend_back.service;

import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * JdbcTemplate과 UserRepository(JPA)는 같은 DataSource를 쓰고, 이 메서드가
 * @Transactional이라 Spring이 둘을 같은 물리 커넥션/트랜잭션으로 동기화합니다.
 * (application.yml에서 spring.jpa.open-in-view=false로 꺼둔 게 이 타이밍을
 * 예측 가능하게 만들어주는 전제조건입니다.)
 */
@Service
@RequiredArgsConstructor
public class RlsContextService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    @Transactional
    public User bootstrap(String uid) {
        jdbcTemplate.update("select set_app_context(?, null, null)", uid);

        User user = userRepository.findById(uid)
                .orElseThrow(() -> new IllegalStateException(
                        "users 테이블에 해당 uid row가 없습니다 (아직 가입 절차가 끝나지 않았을 수 있음): " + uid));

        jdbcTemplate.update(
                "select set_app_context(?, ?::uuid, ?)",
                uid,
                user.getCompanyId() != null ? user.getCompanyId().toString() : null,
                user.getRole() != null ? user.getRole().name() : null
        );

        return user;
    }
}
