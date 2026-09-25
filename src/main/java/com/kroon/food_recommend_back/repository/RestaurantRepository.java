package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** 삭제(soft delete)된 row는 목록/검색에서 빠지도록 DeletedAtIsNull 조건을 붙여 조회합니다. */
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    List<Restaurant> findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(UUID companyId);

    // 등록 전 중복 체크용: 같은 회사 안에서 이름+주소가 유사한 레스토랑 검색
    List<Restaurant> findByCompanyIdAndDeletedAtIsNullAndNameContainingIgnoreCaseAndAddressContainingIgnoreCase(
            UUID companyId, String name, String address);
}
