package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.Restaurant;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class RestaurantResponse {

    private final UUID id;
    private final String name;
    private final String address;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final UUID companyId;
    private final Instant createdAt;
    private final Instant updatedAt;
    /** 내가 등록한 레스토랑인지 */
    private final boolean mine;
    /** 수정/삭제 버튼을 보여줄지 (본인 또는 회사관리자/관리자) */
    private final boolean editable;

    public RestaurantResponse(Restaurant restaurant, boolean mine, boolean editable) {
        this.id = restaurant.getId();
        this.name = restaurant.getName();
        this.address = restaurant.getAddress();
        this.latitude = restaurant.getLatitude();
        this.longitude = restaurant.getLongitude();
        this.companyId = restaurant.getCompanyId();
        this.createdAt = restaurant.getCreatedAt();
        this.updatedAt = restaurant.getUpdatedAt();
        this.mine = mine;
        this.editable = editable;
    }
}
