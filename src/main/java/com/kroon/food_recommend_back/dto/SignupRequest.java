package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * record라서 접근자는 email() / password() / inviteCode() 입니다 (getXxx() 아님).
 * 비밀번호 최소 길이 6은 Firebase Auth의 최소 요구사항과 맞춘 값입니다.
 */
public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 128) String password,
        @NotBlank String inviteCode
) {}
