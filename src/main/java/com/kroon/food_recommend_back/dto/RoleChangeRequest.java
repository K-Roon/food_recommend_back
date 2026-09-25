package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.User;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 관리자 전용 역할/소속 변경 (예: 일반 사용자를 지점 회사관리자로 지정).
 * company_admin / general_user는 companyId 필수, admin은 companyId 없음.
 */
public record RoleChangeRequest(@NotNull User.Role role, UUID companyId) {}
