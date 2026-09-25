package com.kroon.food_recommend_back.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 리뷰 제출. items(메뉴 평점)는 최소 1개 필수 — 레스토랑 단독 리뷰는 불가.
 * restaurantRating/restaurantComment는 선택이지만, 코멘트만 있고 평점이 없으면 거부합니다.
 */
public record ReviewRequest(
        @NotNull UUID restaurantId,
        @Min(1) @Max(5) Integer restaurantRating,
        @Size(max = 1000) String restaurantComment,
        @NotEmpty(message = "메뉴 리뷰를 최소 1개 포함해야 합니다.") @Size(max = 20) List<@Valid ReviewItemRequest> items
) {}
