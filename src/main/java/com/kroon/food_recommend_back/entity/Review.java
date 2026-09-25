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

import java.time.Instant;
import java.util.UUID;

/**
 * 리뷰 1건 = 한 번의 제출. 레스토랑 자체 평점(선택) + 메뉴 평점(review_items, 최소 1개).
 * "레스토랑만 단독 리뷰"는 불가 — ReviewService에서 items가 비어 있으면 거부합니다.
 * company_id 컬럼은 없고, restaurant_id를 통해 회사 스코프가 정해집니다(RLS도 동일).
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId; // Firebase UID

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "restaurant_rating")
    private Short restaurantRating; // 1~5, 선택

    @Column(name = "restaurant_comment")
    private String restaurantComment;

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
