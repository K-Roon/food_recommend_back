package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.User;
import lombok.Getter;

import java.time.Instant;

@Getter
public class UserSummaryResponse {

    private final String id;
    private final String email;
    private final User.Role role;
    private final User.Status status;
    private final String statusReason;
    private final Instant statusChangedAt;
    private final Instant createdAt;

    public UserSummaryResponse(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.statusReason = user.getStatusReason();
        this.statusChangedAt = user.getStatusChangedAt();
        this.createdAt = user.getCreatedAt();
    }
}
