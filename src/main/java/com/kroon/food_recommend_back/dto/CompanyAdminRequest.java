package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 관리자 전용 회사(지점) 생성/수정.
 * 초대코드 예: UBASE_SOONWHA — 영문 대문자/숫자/밑줄 3~40자.
 */
public record CompanyAdminRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^[A-Z0-9_]{3,40}$", message = "영문 대문자/숫자/밑줄 3~40자로 입력해 주세요.") String inviteCode,
        UUID parentCompanyId,
        boolean reviewApprovalRequired
) {}
