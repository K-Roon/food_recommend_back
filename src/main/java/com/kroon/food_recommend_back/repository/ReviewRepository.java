package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** 삭제(soft delete)된 리뷰는 목록과 평점 집계에서 모두 제외합니다. */
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByRestaurantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID restaurantId);

    List<Review> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String userId);

    /** [평균, 개수] — 레스토랑 자체 평점만 (restaurant_rating이 null인 리뷰는 제외). */
    @Query("select avg(r.restaurantRating), count(r.restaurantRating) from Review r "
            + "where r.restaurantId = :restaurantId and r.deletedAt is null")
    List<Object[]> restaurantRatingStats(@Param("restaurantId") UUID restaurantId);
}
