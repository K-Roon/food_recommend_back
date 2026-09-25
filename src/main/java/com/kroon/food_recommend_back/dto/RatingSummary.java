package com.kroon.food_recommend_back.dto;

/**
 * 평점 요약. 리뷰가 하나도 없으면 average는 null (0점과 구분하기 위함).
 * average는 소수점 둘째 자리까지 반올림합니다.
 */
public record RatingSummary(Double average, long count) {

    public static final RatingSummary EMPTY = new RatingSummary(null, 0);

    public static RatingSummary of(Object avg, Object count) {
        long c = count == null ? 0 : ((Number) count).longValue();
        if (c == 0 || avg == null) {
            return EMPTY;
        }
        return new RatingSummary(round(((Number) avg).doubleValue()), c);
    }

    static Double round(Double v) {
        return v == null ? null : Math.round(v * 100.0) / 100.0;
    }
}
