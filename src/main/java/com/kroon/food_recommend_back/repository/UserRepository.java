package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, String> {

    List<User> findByCompanyId(UUID companyId);

    List<User> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    List<User> findAllByOrderByCreatedAtAsc();
}
