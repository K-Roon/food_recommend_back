package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 메뉴 기본 정보 수정 (옵션은 옵션 그룹/옵션 API로 따로 수정).
 * 레스토랑 이동은 지원하지 않습니다 — 잘못 등록했다면 삭제 후 올바른 레스토랑에 다시 등록.
 */
public record MenuUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(0) Integer price,
        String imageUrl,
        String imageSource
) {}
