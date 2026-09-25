package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.NotBlank;

/** 사내 이동 등으로 소속을 바꿀 때 새 지점의 초대코드. */
public record ChangeCompanyRequest(@NotBlank String inviteCode) {}
