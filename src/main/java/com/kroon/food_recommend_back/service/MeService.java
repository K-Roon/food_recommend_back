package com.kroon.food_recommend_back.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.kroon.food_recommend_back.dto.MeResponse;
import com.kroon.food_recommend_back.entity.Company;
import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.UUID;

/**
 * 로그인한 본인 계정 — 조회 / 소속 변경 / 탈퇴.
 * (생성은 SignupService, 비밀번호·이메일 변경은 Firebase Auth를 앱에서 직접 사용)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // RLS 컨텍스트(set_config local)가 같은 트랜잭션 안에서 유지되도록 — RlsContextService 주석 참고
public class MeService {

    private static final Logger log = LoggerFactory.getLogger(MeService.class);

    private final RlsContextService rlsContextService;
    private final CompanyRepository companyRepository;
    private final JdbcTemplate jdbcTemplate;

    public MeResponse me() {
        User user = currentUser();
        Company company = user.getCompanyId() == null ? null
                : companyRepository.findById(user.getCompanyId()).orElse(null);
        return new MeResponse(user, company);
    }

    /**
     * 소속 변경 (사내 이동 등) — 일반 사용자만. 새 초대코드의 회사로 옮깁니다.
     * 내가 등록한 레스토랑/메뉴/리뷰는 이전 회사에 그대로 남습니다 (그 회사의 공용 정보).
     */
    @Transactional
    public MeResponse changeCompany(String inviteCode) {
        User user = currentUser();
        UUID newCompanyId;
        try {
            newCompanyId = jdbcTemplate.queryForObject("select change_my_company(?)", UUID.class, inviteCode.trim());
        } catch (DataAccessException e) {
            String state = sqlState(e);
            if ("P0002".equals(state)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 초대코드입니다.");
            }
            if ("42501".equals(state)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "일반 사용자만 초대코드를 변경할 수 있습니다.");
            }
            throw e;
        }
        // 이후 조회가 새 회사 기준으로 RLS를 통과하도록 컨텍스트 갱신
        rlsContextService.setContext(user.getId(), newCompanyId.toString(), user.getRole().name());
        user.setCompanyId(newCompanyId);
        return new MeResponse(user, companyRepository.findById(newCompanyId).orElse(null));
    }

    /**
     * 회원 탈퇴.
     * 1) DB: 상태 withdrawn + 내가 쓴 리뷰를 'withdrawal' 사유로 삭제 (withdraw_current_user())
     *    — 내가 등록한 레스토랑/메뉴는 회사 공용 정보라 남겨둡니다
     * 2) Firebase 계정 삭제 — 같은 이메일로 다시 가입할 수 있도록
     * Firebase 삭제가 실패하면 예외로 DB 변경도 롤백됩니다.
     */
    @Transactional
    public void withdraw() {
        User user = currentUser();
        jdbcTemplate.queryForObject("select withdraw_current_user()", Object.class);
        try {
            FirebaseAuth.getInstance().deleteUser(user.getId());
        } catch (FirebaseAuthException e) {
            log.error("탈퇴 중 Firebase 계정 삭제 실패 uid={}", user.getId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "탈퇴 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static String sqlState(DataAccessException e) {
        Throwable t = e.getMostSpecificCause();
        return (t instanceof SQLException sql) ? sql.getSQLState() : null;
    }

    private User currentUser() {
        String uid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return rlsContextService.bootstrap(uid);
    }
}
