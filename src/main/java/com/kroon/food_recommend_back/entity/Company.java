package com.kroon.food_recommend_back.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "invite_code", nullable = false)
    private String inviteCode;

    @Column(name = "parent_company_id")
    private UUID parentCompanyId;

    @Column(name = "review_approval_required", nullable = false)
    private boolean reviewApprovalRequired;

    @Column(name = "public_visibility", nullable = false)
    private boolean publicVisibility;

    @Column(name = "ad_free", nullable = false)
    private boolean adFree;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.Instant updatedAt;

    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    protected Company() {}

    /** 관리자 회사 생성용 */
    public Company(String name, String inviteCode, UUID parentCompanyId) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.parentCompanyId = parentCompanyId;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getInviteCode() { return inviteCode; }
    public UUID getParentCompanyId() { return parentCompanyId; }
    public boolean isReviewApprovalRequired() { return reviewApprovalRequired; }
    public boolean isPublicVisibility() { return publicVisibility; }
    public boolean isAdFree() { return adFree; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public java.time.Instant getDeletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }

    public void setName(String name) { this.name = name; touch(); }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; touch(); }
    public void setParentCompanyId(UUID parentCompanyId) { this.parentCompanyId = parentCompanyId; touch(); }
    public void setReviewApprovalRequired(boolean v) { this.reviewApprovalRequired = v; touch(); }

    public void markDeleted(String by) {
        this.deletedAt = java.time.Instant.now();
        this.deletedBy = by;
    }

    private void touch() { this.updatedAt = java.time.Instant.now(); }
}
