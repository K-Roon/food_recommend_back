package com.kroon.food_recommend_back.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    private String id; // Firebase UID

    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    private UUID companyId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant createdAt;

    public enum Role { admin, company_admin, general_user }
    public enum Status { active, deleted }
}
