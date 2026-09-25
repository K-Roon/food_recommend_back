package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.dto.DeleteRequest;
import com.kroon.food_recommend_back.dto.MenuReviewsResponse;
import com.kroon.food_recommend_back.dto.RestaurantRatingResponse;
import com.kroon.food_recommend_back.dto.RestaurantReviewsResponse;
import com.kroon.food_recommend_back.dto.ReviewRequest;
import com.kroon.food_recommend_back.dto.ReviewResponse;
import com.kroon.food_recommend_back.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 리뷰 작성 — 레스토랑 평점(선택) + 메뉴 평점(1개 이상). */
    @PostMapping("/api/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@Valid @RequestBody ReviewRequest request) {
        return reviewService.create(request);
    }

    @GetMapping("/api/reviews/me")
    public List<ReviewResponse> mine() {
        return reviewService.listMine();
    }

    @GetMapping("/api/reviews/{reviewId}")
    public ReviewResponse get(@PathVariable UUID reviewId) {
        return reviewService.get(reviewId);
    }

    /** 수정 — 작성자 본인만. 레스토랑 평점/코멘트 + 메뉴 항목 전체를 요청 내용으로 교체. */
    @PutMapping("/api/reviews/{reviewId}")
    public ReviewResponse update(@PathVariable UUID reviewId, @Valid @RequestBody ReviewRequest request) {
        return reviewService.update(reviewId, request);
    }

    /** 삭제 — 본인이면 본인삭제, 회사관리자/관리자가 남의 리뷰를 지우면 규정위반 삭제(body.note 필수). */
    @DeleteMapping("/api/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID reviewId, @Valid @RequestBody(required = false) DeleteRequest request) {
        reviewService.delete(reviewId, DeleteRequest.noteOf(request));
    }

    /** 레스토랑 평점 + 그 레스토랑 메뉴들의 리뷰 전체. */
    @GetMapping("/api/restaurants/{restaurantId}/reviews")
    public RestaurantReviewsResponse restaurantReviews(@PathVariable UUID restaurantId) {
        return reviewService.listForRestaurant(restaurantId);
    }

    /** 레스토랑 평점만 (가벼운 조회용). */
    @GetMapping("/api/restaurants/{restaurantId}/rating")
    public RestaurantRatingResponse restaurantRating(@PathVariable UUID restaurantId) {
        return reviewService.restaurantRating(restaurantId);
    }

    /** 특정 메뉴의 평점 + 코멘트 목록. */
    @GetMapping("/api/menus/{menuId}/reviews")
    public MenuReviewsResponse menuReviews(@PathVariable UUID menuId) {
        return reviewService.listForMenu(menuId);
    }
}
