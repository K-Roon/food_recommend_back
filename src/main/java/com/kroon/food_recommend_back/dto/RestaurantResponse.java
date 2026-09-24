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

    public RestaurantResponse(Restaurant restaurant) {
        this.id = restaurant.getId();
        this.name = restaurant.getName();
        this.address = restaurant.getAddress();
        this.latitude = restaurant.getLatitude();
        this.longitude = restaurant.getLongitude();
        this.companyId = restaurant.getCompanyId();
        this.createdAt = restaurant.getCreatedAt();
    }
}