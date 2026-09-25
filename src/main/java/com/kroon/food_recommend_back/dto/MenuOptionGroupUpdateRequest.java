package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.NotBlank;

/** 옵션 그룹 자체 속성 수정 (그룹 안의 옵션은 옵션 API로 개별 추가/수정/삭제). */
public record MenuOptionGroupUpdateRequest(
        @NotBlank String title,
        boolean multiSelect,
        boolean required,
        Integer sortOrder
) {}
