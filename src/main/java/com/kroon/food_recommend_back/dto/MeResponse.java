package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.Company;
import com.kroon.food_recommend_back.entity.User;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** 로그인한 본인 정보 — 엔티티를 그대로 노출하지 않고 앱에 필요한 값만 내려줍니다. */
@Getter
public class MeResponse {

    private final String id;
    private final String email;
    private final User.Role role;
    private final User.Status status;
    private final UUID companyId;
    private final String companyName;
    private final boolean adFree;
    private final Instant createdAt;

    public MeResponse(User user, Company company) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.companyId = user.getCompanyId();
        this.companyName = company != null ? company.getName() : null;
        this.adFree = company != null && company.isAdFree();
        this.createdAt = user.getCreatedAt();
    }
}
