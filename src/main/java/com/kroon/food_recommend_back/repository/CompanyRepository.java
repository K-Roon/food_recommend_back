package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByInviteCode(String inviteCode);
}