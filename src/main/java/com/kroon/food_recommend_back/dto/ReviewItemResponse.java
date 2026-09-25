package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.ReviewItem;

import java.util.UUID;

public record ReviewItemResponse(UUID id, UUID menuId, String menuName, int rating, String comment) {

    public static ReviewItemResponse of(ReviewItem item, String menuName) {
        return new ReviewItemResponse(item.getId(), item.getMenuId(), menuName, item.getRating(), item.getComment());
    }
}
