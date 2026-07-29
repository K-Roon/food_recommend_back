package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
