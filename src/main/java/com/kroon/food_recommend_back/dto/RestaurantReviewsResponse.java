package com.kroon.food_recommend_back.dto;

import java.util.List;
import java.util.UUID;

/** 레스토랑 검색/상세에서 쓰는 응답 — 레스토랑 평점 요약 + 그 레스토랑 메뉴들의 리뷰 목록. */
public record RestaurantReviewsResponse(
        UUID restaurantId,
        String restaurantName,
        RestaurantRatingResponse rating,
        List<ReviewResponse> reviews
) {}
