package com.kroon.food_recommend_back.service;

import com.kroon.food_recommend_back.dto.CompanyAdminRequest;
import com.kroon.food_recommend_back.dto.CompanyResponse;
import com.kroon.food_recommend_back.dto.CompanySettingsRequest;
import com.kroon.food_recommend_back.entity.Company;
import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.repository.CompanyRepository;
import com.kroon.food_recommend_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * 회사(지점) CRUD.
 * - 내 회사 조회: 누구나 / 설정 변경(메뉴 승인 필요 여부): 회사관리자·관리자
 * - 생성/전체 조회/수정/삭제: 관리자 전용
 * 삭제는 soft delete이고, 활성 사용자가 남아 있으면 거부합니다(먼저 이동/제명 필요).
 * 삭제된 회사의 초대코드로는 가입/소속 변경이 안 됩니다(DB 함수에서 제외).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // RLS 컨텍스트(set_config local)가 같은 트랜잭션 안에서 유지되도록 — RlsContextService 주석 참고
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RlsContextService rlsContextService;

    // ------------------------------------------------------------- 내 회사

    public CompanyResponse myCompany() {
        User user = currentUser();
        if (user.getCompanyId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "소속 회사가 없는 계정입니다.");
        }
        return CompanyResponse.of(findAliveOrThrow(user.getCompanyId()), Permissions.isReviewer(user));
    }

    @Transactional
    public CompanyResponse updateMySettings(CompanySettingsRequest request) {
        User user = currentUser();
        Permissions.requireReviewer(user);
        Company company = findAliveOrThrow(user.getCompanyId());
        company.setReviewApprovalRequired(request.reviewApprovalRequired());
        return CompanyResponse.of(companyRepository.save(company), true);
    }

    // ------------------------------------------------------------- 관리자

    public List<CompanyResponse> listAll() {
        Permissions.requireAdmin(currentUser());
        return companyRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(c -> CompanyResponse.of(c, true))
                .toList();
    }

    public CompanyResponse get(UUID companyId) {
        Permissions.requireAdmin(currentUser());
        return CompanyResponse.of(findAliveOrThrow(companyId), true);
    }

    @Transactional
    public CompanyResponse create(CompanyAdminRequest request) {
        Permissions.requireAdmin(currentUser());
        if (companyRepository.existsByInviteCode(request.inviteCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 초대코드입니다.");
        }
        validateParent(null, request.parentCompanyId());
        Company company = new Company(request.name().trim(), request.inviteCode(), request.parentCompanyId());
        company.setReviewApprovalRequired(request.reviewApprovalRequired());
        return CompanyResponse.of(companyRepository.saveAndFlush(company), true);
    }

    @Transactional
    public CompanyResponse update(UUID companyId, CompanyAdminRequest request) {
        Permissions.requireAdmin(currentUser());
        Company company = findAliveOrThrow(companyId);
        if (!company.getInviteCode().equals(request.inviteCode()) && companyRepository.existsByInviteCode(request.inviteCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 초대코드입니다.");
        }
        validateParent(companyId, request.parentCompanyId());
        company.setName(request.name().trim());
        company.setInviteCode(request.inviteCode());
        company.setParentCompanyId(request.parentCompanyId());
        company.setReviewApprovalRequired(request.reviewApprovalRequired());
        return CompanyResponse.of(companyRepository.save(company), true);
    }

    @Transactional
    public void delete(UUID companyId) {
        User admin = currentUser();
        Permissions.requireAdmin(admin);
        Company company = findAliveOrThrow(companyId);
        boolean hasActiveUsers = userRepository.findByCompanyId(companyId).stream()
                .anyMatch(u -> u.getStatus() == User.Status.active);
        if (hasActiveUsers) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "활성 사용자가 남아 있는 회사는 삭제할 수 없습니다. 먼저 이동하거나 제명해 주세요.");
        }
        company.markDeleted(admin.getId());
        companyRepository.save(company);
    }

    private void validateParent(UUID selfId, UUID parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신을 상위 회사로 지정할 수 없습니다.");
        }
        findAliveOrThrow(parentId);
    }

    private Company findAliveOrThrow(UUID companyId) {
        return companyRepository.findById(companyId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회사를 찾을 수 없습니다."));
    }

    private User currentUser() {
        String uid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return rlsContextService.bootstrap(uid);
    }
}
