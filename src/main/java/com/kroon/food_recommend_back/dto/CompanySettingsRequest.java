package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.NotNull;

/** 회사관리자가 바꿀 수 있는 자기 회사 설정. (공개 범위/광고 제거는 아직 비활성이라 제외) */
public record CompanySettingsRequest(@NotNull Boolean reviewApprovalRequired) {}
