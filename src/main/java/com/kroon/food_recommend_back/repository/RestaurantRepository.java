package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    List<Restaurant> findByCompanyId(UUID companyId);

    // 등록 전 중복 체크용: 같은 회사 안에서 이름+주소가 유사한 레스토랑 검색
    List<Restaurant> findByCompanyIdAndNameContainingIgnoreCaseAndAddressContainingIgnoreCase(
            UUID companyId, String name, String address);
}