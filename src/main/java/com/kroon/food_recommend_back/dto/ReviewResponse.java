package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.Review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 작성자 정보는 내려주지 않습니다 — users RLS상 다른 사람 row는 조회할 수 없고,
 * 사내 리뷰라 익명성이 오히려 솔직한 리뷰에 유리합니다. 대신 mine으로 본인 여부만 표시.
 */
public record ReviewResponse(
        UUID id,
        UUID restaurantId,
        String restaurantName,
        Integer restaurantRating,
        String restaurantComment,
        boolean mine,
        /** 삭제 버튼 노출 여부 (본인 또는 회사관리자/관리자). 수정은 mine일 때만 */
        boolean deletable,
        java.time.Instant updatedAt,
        Instant createdAt,
        List<ReviewItemResponse> items
) {
    public static ReviewResponse of(Review review, String restaurantName, boolean mine, boolean deletable,
                                    List<ReviewItemResponse> items) {
        return new ReviewResponse(
                review.getId(),
                review.getRestaurantId(),
                restaurantName,
                review.getRestaurantRating() == null ? null : review.getRestaurantRating().intValue(),
                review.getRestaurantComment(),
                mine,
                deletable,
                review.getUpdatedAt(),
                review.getCreatedAt(),
                items);
    }
}
