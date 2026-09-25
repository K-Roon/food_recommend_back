package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.MenuOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuOptionGroupRepository extends JpaRepository<MenuOptionGroup, UUID> {

    List<MenuOptionGroup> findByMenuIdOrderBySortOrderAsc(UUID menuId);
}
