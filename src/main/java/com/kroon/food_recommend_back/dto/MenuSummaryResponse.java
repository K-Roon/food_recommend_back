package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.Menu;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** 기본 목록 뷰용 - 레스토랑 이름은 참고용으로만 포함하고, 옵션은 상세 조회에서만 내려줍니다. */
@Getter
public class MenuSummaryResponse {

    private final UUID id;
    private final UUID restaurantId;
    private final String restaurantName;
    private final String name;
    private final Integer price;
    private final String imageUrl;
    private final Menu.ApprovalStatus approvalStatus;
    private final Instant createdAt;
    /** 메뉴 평점 (리뷰 없으면 average=null, count=0) */
    private final RatingSummary rating;

    public MenuSummaryResponse(Menu menu, String restaurantName, RatingSummary rating) {
        this.id = menu.getId();
        this.restaurantId = menu.getRestaurantId();
        this.restaurantName = restaurantName;
        this.name = menu.getName();
        this.price = menu.getPrice();
        this.imageUrl = menu.getImageUrl();
        this.approvalStatus = menu.getApprovalStatus();
        this.createdAt = menu.getCreatedAt();
        this.rating = rating;
    }
}
