package com.kroon.food_recommend_back.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    private String id; // Firebase UID

    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Role role;

    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Status status;

    private String statusReason;
    private Instant statusChangedAt;

    private Instant createdAt;

    public enum Role { admin, company_admin, general_user }
    /**
     * DB의 user_status enum과 값이 정확히 같아야 합니다 (하나라도 빠지면 그 상태의 유저를 읽는 순간 예외).
     * - deleted  : 회사관리자가 회사에서 제명 (CompanyAdminService.removeFromCompany)
     * - withdrawn: 본인이 직접 탈퇴 (탈퇴 API는 아직 없음)
     */
    public enum Status { active, suspended, banned, deleted, withdrawn }
}