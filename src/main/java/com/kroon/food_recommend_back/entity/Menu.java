package com.kroon.food_recommend_back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 회사 스코프 테이블입니다 (company_id 보유) - Restaurant과 동일한 스코프 원칙.
 * approval_status: 회사가 review_approval_required=true로 설정한 경우 pending으로
 * 생성되고, 회사관리자/관리자가 승인(approve)하기 전까지 공개 목록(list)에
 * 노출되지 않습니다 (auto_approved/approved만 공개).
 * is_bookable / priority_score: 예약·우선노출 기능을 위해 스키마에는 존재하지만
 * 아직 로직을 붙이지 않은 placeholder 컬럼입니다 - API로 값을 받지 않고 항상
 * false/0으로 저장합니다.
 */
@Entity
@Table(name = "menus")
@Getter
@Setter
@NoArgsConstructor
public class Menu {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "created_by", nullable = false)
    private String createdBy; // Firebase UID

    private String name;

    private Integer price;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "image_source")
    private String imageSource;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "approval_status", nullable = false)
    private ApprovalStatus approvalStatus;

    @Column(name = "is_bookable", nullable = false)
    private boolean bookable;

    @Column(name = "priority_score", nullable = false)
    private Integer priorityScore;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ---- soft delete (01_schema_crud.sql) ----
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "delete_reason")
    private DeleteReason deleteReason;

    @Column(name = "delete_note")
    private String deleteNote;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted(String by, DeleteReason reason, String note) {
        this.deletedAt = Instant.now();
        this.deletedBy = by;
        this.deleteReason = reason;
        this.deleteNote = note;
    }

    public enum ApprovalStatus { auto_approved, pending, approved, rejected }
}
