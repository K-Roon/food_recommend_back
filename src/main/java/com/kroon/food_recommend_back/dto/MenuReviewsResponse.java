package com.kroon.food_recommend_back.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 메뉴 상세 화면용 — 이 메뉴의 평점 요약 + 메뉴별 코멘트 목록(최신순). */
public record MenuReviewsResponse(UUID menuId, RatingSummary rating, List<Entry> reviews) {

    public record Entry(UUID reviewItemId, UUID reviewId, int rating, String comment, boolean mine, Instant createdAt) {}
}
