package com.kroon.food_recommend_back.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.kroon.food_recommend_back.dto.RoleChangeRequest;
import com.kroon.food_recommend_back.dto.SanctionRequest;
import com.kroon.food_recommend_back.dto.UserSummaryResponse;
import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.CompanyRepository;
import com.kroon.food_recommend_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 회사관리자(company_admin) 전용 소속 유저 관리 + 관리자(admin) 전용 앱 전체 밴.
 *
 * 제재 3단계:
 * - suspend/reactivate: company_admin이 자기 회사 소속 general_user만 대상으로
 *   가능한 일시 정지/해제 (status: active <-> suspended). 히스토리 테이블은
 *   아직 스키마가 없어서 이번 라운드에는 안 만들었습니다 — users.status_reason/
 *   status_changed_at에 마지막 사유만 남습니다.
 * - removeFromCompany: 회사에서 제명. 메모리 결정상 "영구 삭제"지만, users
 *   테이블에 이미 status enum에 deleted가 있어서 실제 row는 지우지 않고
 *   soft delete(status=deleted)로 처리했습니다 — 다른 테이블(menus.created_by,
 *   reviews 등)이 이 uid를 참조하고 있어서 하드 delete하면 참조 무결성이 깨집니다.
 *   실제로 "삭제됐다"고 취급하려면 API 레이어에서 status=deleted인 유저를
 *   걸러내는 걸 잊지 않아야 합니다 (아직 다른 서비스들은 이 필터링을 안 하고
 *   있음 — 다음 라운드에 정리 필요).
 * - ban: admin 전용, 회사 무관 앱 전체 정지.
 *
 * 세 경우 모두 Firebase 계정도 같이 비활성화(disabled=true)해서 실제로 로그인이
 * 막히게 했습니다. 이메일 통지(초대코드 제명 안내 등)는 아직 이메일 발송
 * 연동이 없어서 구현하지 않았습니다 — TODO로 남겨둡니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // RLS 컨텍스트(set_config local)가 같은 트랜잭션 안에서 유지되도록 — RlsContextService 주석 참고
public class CompanyAdminService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RlsContextService rlsContextService;

    public List<UserSummaryResponse> listCompanyUsers() {
        User actor = currentUser();
        requireCompanyAdminOrAdmin(actor);
        if (actor.getCompanyId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "소속 회사가 없는 계정입니다.");
        }
        return userRepository.findByCompanyIdOrderByCreatedAtAsc(actor.getCompanyId()).stream()
                .map(UserSummaryResponse::new)
                .toList();
    }

    // ------------------------------------------------------------- 관리자 전용 조회/역할

    /** 관리자 — companyId를 주면 그 회사 사용자만, 없으면 전체. */
    public List<UserSummaryResponse> listAllUsers(UUID companyId) {
        Permissions.requireAdmin(currentUser());
        List<User> users = companyId == null
                ? userRepository.findAllByOrderByCreatedAtAsc()
                : userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
        return users.stream().map(UserSummaryResponse::new).toList();
    }

    public UserSummaryResponse getUser(String uid) {
        Permissions.requireAdmin(currentUser());
        return new UserSummaryResponse(userRepository.findById(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")));
    }

    /** 관리자 — 역할/소속 지정 (예: 지점 회사관리자 임명). DB 트리거도 관리자만 허용. */
    @Transactional
    public UserSummaryResponse changeRole(String targetUid, RoleChangeRequest request) {
        User actor = currentUser();
        Permissions.requireAdmin(actor);
        if (actor.getId().equals(targetUid)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신의 역할은 바꿀 수 없습니다.");
        }
        User target = userRepository.findById(targetUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        UUID companyId = request.companyId();
        if (request.role() == User.Role.admin) {
            companyId = null;
        } else {
            if (companyId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회사관리자/일반 사용자는 소속 회사(companyId)가 필요합니다.");
            }
            companyRepository.findById(companyId)
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 회사입니다."));
        }
        target.setRole(request.role());
        target.setCompanyId(companyId);
        return new UserSummaryResponse(userRepository.save(target));
    }

    // ------------------------------------------------------------- 제재

    @Transactional
    public void suspend(String targetUid, SanctionRequest request) {
        User target = requireManageableTarget(targetUid);
        target.setStatus(User.Status.suspended);
        target.setStatusReason(request.getReason());
        target.setStatusChangedAt(Instant.now());
        userRepository.save(target);
        setFirebaseDisabled(targetUid, true);
    }

    @Transactional
    public void reactivate(String targetUid) {
        User target = requireManageableTarget(targetUid);
        if (target.getStatus() == User.Status.withdrawn) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "탈퇴한 계정은 복구할 수 없습니다. 다시 가입해야 합니다.");
        }
        if (target.getStatus() == User.Status.banned && currentUser().getRole() != User.Role.admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "앱 전체 이용 제한은 관리자만 해제할 수 있습니다.");
        }
        target.setStatus(User.Status.active);
        target.setStatusReason(null);
        target.setStatusChangedAt(Instant.now());
        userRepository.save(target);
        setFirebaseDisabled(targetUid, false);
    }

    @Transactional
    public void removeFromCompany(String targetUid, SanctionRequest request) {
        User target = requireManageableTarget(targetUid);
        target.setStatus(User.Status.deleted);
        target.setStatusReason(request.getReason());
        target.setStatusChangedAt(Instant.now());
        userRepository.save(target);
        setFirebaseDisabled(targetUid, true);
        // TODO: 개인 이메일로 초대코드 제명 통지 메일 발송 (이메일 발송 연동 후 구현)
    }

    @Transactional
    public void ban(String targetUid, SanctionRequest request) {
        User actor = currentUser();
        if (actor.getRole() != User.Role.admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다.");
        }
        User target = userRepository.findById(targetUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (target.getRole() == User.Role.admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 관리자는 제재할 수 없습니다.");
        }

        target.setStatus(User.Status.banned);
        target.setStatusReason(request.getReason());
        target.setStatusChangedAt(Instant.now());
        userRepository.save(target);
        setFirebaseDisabled(targetUid, true);
    }

    /** company_admin은 자기 회사의 general_user만, admin은 전체를 대상으로 삼을 수 있습니다. */
    private User requireManageableTarget(String targetUid) {
        User actor = currentUser();
        requireCompanyAdminOrAdmin(actor);

        User target = userRepository.findById(targetUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (actor.getRole() == User.Role.company_admin) {
            if (actor.getCompanyId() == null || !actor.getCompanyId().equals(target.getCompanyId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 회사 소속 사용자입니다.");
            }
            if (target.getRole() != User.Role.general_user) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "일반 사용자만 제재할 수 있습니다.");
            }
        }
        return target;
    }

    private void requireCompanyAdminOrAdmin(User user) {
        if (user.getRole() != User.Role.company_admin && user.getRole() != User.Role.admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }
    }

    private void setFirebaseDisabled(String uid, boolean disabled) {
        try {
            FirebaseAuth.getInstance().updateUser(new UserRecord.UpdateRequest(uid).setDisabled(disabled));
            if (disabled) {
                // 이미 로그인된 기기의 refresh token도 무효화 → 다음 토큰 갱신 시점에 로그아웃됨
                FirebaseAuth.getInstance().revokeRefreshTokens(uid);
            }
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Firebase 계정 상태 변경에 실패했습니다: " + e.getMessage());
        }
    }

    private User currentUser() {
        String uid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return rlsContextService.bootstrap(uid);
    }
}
