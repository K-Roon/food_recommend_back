package com.kroon.food_recommend_back.dto;

/**
 * 레스토랑 평점 = (메뉴 리뷰 평균 + 레스토랑 자체 평균) / 2
 * 한쪽만 있으면 있는 쪽 값을 그대로 쓰고, 둘 다 없으면 null.
 *
 * @param rating           최종 레스토랑 평점
 * @param menuRating       이 레스토랑 모든 메뉴 리뷰의 평균/개수
 * @param restaurantRating 레스토랑 자체 평점의 평균/개수
 */
public record RestaurantRatingResponse(Double rating, RatingSummary menuRating, RatingSummary restaurantRating) {

    public static RestaurantRatingResponse of(RatingSummary menu, RatingSummary restaurant) {
        Double combined;
        if (menu.average() != null && restaurant.average() != null) {
            combined = RatingSummary.round((menu.average() + restaurant.average()) / 2.0);
        } else if (menu.average() != null) {
            combined = menu.average();
        } else {
            combined = restaurant.average();
        }
        return new RestaurantRatingResponse(combined, menu, restaurant);
    }
}
