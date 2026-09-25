package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.Company;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * inviteCode는 회사관리자/관리자에게만 내려줍니다 (일반 사용자 응답에선 null).
 * publicVisibility / adFree는 아직 기능이 비활성이라 읽기 전용으로만 노출합니다.
 */
public record CompanyResponse(
        UUID id,
        String name,
        String inviteCode,
        UUID parentCompanyId,
        boolean reviewApprovalRequired,
        boolean publicVisibility,
        boolean adFree,
        OffsetDateTime createdAt,
        Instant updatedAt
) {
    public static CompanyResponse of(Company c, boolean showInviteCode) {
        return new CompanyResponse(c.getId(), c.getName(), showInviteCode ? c.getInviteCode() : null,
                c.getParentCompanyId(), c.isReviewApprovalRequired(), c.isPublicVisibility(), c.isAdFree(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
