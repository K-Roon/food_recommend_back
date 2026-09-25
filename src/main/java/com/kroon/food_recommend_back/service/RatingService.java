package com.kroon.food_recommend_back.service;

import com.kroon.food_recommend_back.dto.RatingSummary;
import com.kroon.food_recommend_back.dto.RestaurantRatingResponse;
import com.kroon.food_recommend_back.repository.ReviewItemRepository;
import com.kroon.food_recommend_back.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 평점 집계 전용. 다른 서비스(메뉴/리뷰)의 트랜잭션 안에서 호출되는 걸 전제로 합니다
 * (RLS 컨텍스트가 이미 걸린 상태 — 전파 REQUIRED로 합류).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingService {

    private final ReviewRepository reviewRepository;
    private final ReviewItemRepository reviewItemRepository;

    public Map<UUID, RatingSummary> menuRatings(Collection<UUID> menuIds) {
        Map<UUID, RatingSummary> result = new HashMap<>();
        if (menuIds.isEmpty()) {
            return result;
        }
        for (Object[] row : reviewItemRepository.menuRatingStats(menuIds)) {
            result.put((UUID) row[0], RatingSummary.of(row[1], row[2]));
        }
        return result;
    }

    public RatingSummary menuRating(UUID menuId) {
        return menuRatings(List.of(menuId)).getOrDefault(menuId, RatingSummary.EMPTY);
    }

    public RestaurantRatingResponse restaurantRating(UUID restaurantId) {
        RatingSummary menu = first(reviewItemRepository.restaurantMenuRatingStats(restaurantId));
        RatingSummary own = first(reviewRepository.restaurantRatingStats(restaurantId));
        return RestaurantRatingResponse.of(menu, own);
    }

    private static RatingSummary first(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return RatingSummary.EMPTY;
        }
        Object[] row = rows.get(0);
        return RatingSummary.of(row[0], row[1]);
    }
}
