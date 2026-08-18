package com.kroon.food_recommend_back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor
public class Company {

    @Id
    private UUID id;

    private String name;

    @Column(name = "invite_code")
    private String inviteCode;

    @Column(name = "parent_company_id")
    private UUID parentCompanyId;

    @Column(name = "review_approval_required")
    private boolean reviewApprovalRequired;

    @Column(name = "public_visibility")
    private boolean publicVisibility;

    @Column(name = "ad_free")
    private boolean adFree;

    @Column(name = "created_at")
    private Instant createdAt;
}