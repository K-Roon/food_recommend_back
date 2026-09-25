package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.Menu;
import com.kroon.food_recommend_back.entity.Restaurant;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 메뉴 상세 - 탭했을 때 레스토랑 위치 정보 + 옵션 그룹까지 같이 내려줍니다. */
@Getter
public class MenuResponse {

    private final UUID id;
    private final String name;
    private final Integer price;
    private final String imageUrl;
    private final String imageSource;
    private final Menu.ApprovalStatus approvalStatus;
    private final Instant createdAt;
    private final UUID restaurantId;
    private final String restaurantName;
    private final String restaurantAddress;
    private final BigDecimal restaurantLatitude;
    private final BigDecimal restaurantLongitude;
    private final List<MenuOptionGroupResponse> optionGroups;
    /** 이 메뉴의 평점 */
    private final RatingSummary rating;
    /** 메뉴를 탭했을 때 보여줄 레스토랑 평점 */
    private final RestaurantRatingResponse restaurantRating;
    private final Instant updatedAt;
    /** 내가 등록한 메뉴인지 */
    private final boolean mine;
    /** 수정/삭제 버튼을 보여줄지 (본인 또는 회사관리자/관리자) */
    private final boolean editable;

    public MenuResponse(Menu menu, Restaurant restaurant, List<MenuOptionGroupResponse> optionGroups,
                        RatingSummary rating, RestaurantRatingResponse restaurantRating,
                        boolean mine, boolean editable) {
        this.id = menu.getId();
        this.name = menu.getName();
        this.price = menu.getPrice();
        this.imageUrl = menu.getImageUrl();
        this.imageSource = menu.getImageSource();
        this.approvalStatus = menu.getApprovalStatus();
        this.createdAt = menu.getCreatedAt();
        this.restaurantId = restaurant.getId();
        this.restaurantName = restaurant.getName();
        this.restaurantAddress = restaurant.getAddress();
        this.restaurantLatitude = restaurant.getLatitude();
        this.restaurantLongitude = restaurant.getLongitude();
        this.optionGroups = optionGroups;
        this.rating = rating;
        this.restaurantRating = restaurantRating;
        this.updatedAt = menu.getUpdatedAt();
        this.mine = mine;
        this.editable = editable;
    }
}
