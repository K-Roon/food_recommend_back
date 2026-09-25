package com.kroon.food_recommend_back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 회사 스코프 테이블입니다 (company_id 보유) - 각 회사가 등록한 레스토랑은
 * 그 회사 소속 사용자에게만 보입니다. 같은 실제 가게라도 회사마다 별도
 * row로 중복 등록될 수 있습니다 (의도된 트레이드오프 - private-by-default
 * 원칙을 우선하고, 중복은 등록 전 검색 + 관리자 병합 기능으로 완화 예정).
 */
@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

    @Id
    private UUID id;

    private String name;

    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "created_by")
    private String createdBy; // Firebase UID

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
}
