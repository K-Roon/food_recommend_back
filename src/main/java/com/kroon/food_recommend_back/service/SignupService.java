package com.kroon.food_recommend_back.service;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.kroon.food_recommend_back.dto.SignupRequest;
import com.kroon.food_recommend_back.dto.SignupResponse;
import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * 초대코드 기반 회원가입.
 * 1. invite_code로 회사 조회
 * 2. Firebase Admin SDK로 계정 생성 (비밀번호는 여기서만 다루고 DB엔 저장하지 않음)
 * 3. users row 생성 (role=general_user, status=active)
 *
 * RLS 대응:
 * - 가입 시점엔 로그인 컨텍스트가 없어서 companies를 직접 조회할 수 없습니다
 *   (companies는 "내 회사만 보기" 정책). 그래서 초대코드 → company_id 변환만
 *   해주는 SECURITY DEFINER 함수 company_id_by_invite_code()를 씁니다.
 *   (레포 루트 db/rls_policies.sql 에서 생성 — 이 SQL을 먼저 실행해야 회원가입이 동작합니다)
 * - users INSERT 전에 set_app_context(새 uid, company_id, 'general_user')를 걸어서
 *   "본인 row만, general_user로만 insert 가능" 정책을 통과시킵니다.
 *
 * 원자성: Firebase 계정 생성 후 DB insert가 실패하면 Firebase 계정을 지웁니다.
 * saveAndFlush로 INSERT를 즉시 실행해야 제약조건 위반 같은 DB 오류를 try 안에서
 * 잡을 수 있습니다 (save만 하면 커밋 시점에 터져서 Firebase 계정이 고아로 남음).
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    private static final Logger log = LoggerFactory.getLogger(SignupService.class);

    private final UserRepository userRepository;
    private final RlsContextService rlsContextService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        UUID companyId = findCompanyIdByInviteCode(request.inviteCode().trim());

        UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                .setEmail(request.email())
                .setPassword(request.password())
                .setEmailVerified(false);

        UserRecord firebaseUser;
        try {
            firebaseUser = FirebaseAuth.getInstance().createUser(createRequest);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "계정 생성에 실패했습니다: " + e.getMessage());
        }

        try {
            rlsContextService.setContext(firebaseUser.getUid(), companyId.toString(), User.Role.general_user.name());

            User user = new User();
            user.setId(firebaseUser.getUid());
            user.setEmail(request.email());
            user.setRole(User.Role.general_user);
            user.setCompanyId(companyId);
            user.setStatus(User.Status.active);
            user.setCreatedAt(Instant.now());
            userRepository.saveAndFlush(user);
        } catch (Exception e) {
            log.error("회원가입 DB 처리 실패 — Firebase 계정 롤백 uid={}", firebaseUser.getUid(), e);
            try {
                FirebaseAuth.getInstance().deleteUser(firebaseUser.getUid());
            } catch (FirebaseAuthException rollbackError) {
                log.error("Firebase 계정 롤백도 실패 — 수동 정리 필요 uid={}", firebaseUser.getUid(), rollbackError);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "회원가입 처리 중 오류가 발생했습니다.");
        }

        return new SignupResponse(firebaseUser.getUid(), firebaseUser.getEmail());
    }

    private UUID findCompanyIdByInviteCode(String inviteCode) {
        try {
            UUID id = jdbcTemplate.queryForObject("select company_id_by_invite_code(?)", UUID.class, inviteCode);
            if (id != null) {
                return id;
            }
        } catch (EmptyResultDataAccessException ignored) {
            // fall through
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 초대코드입니다.");
    }
}
