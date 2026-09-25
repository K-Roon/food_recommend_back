package com.kroon.food_recommend_back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** 리뷰 안의 메뉴별 평점 + 코멘트. */
@Entity
@Table(name = "review_items")
@Getter
@Setter
@NoArgsConstructor
public class ReviewItem {

    @Id
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "menu_id", nullable = false)
    private UUID menuId;

    @Column(name = "rating", nullable = false)
    private Short rating; // 1~5

    private String comment;

    @Column(name = "created_at")
    private Instant createdAt;
}
