package com.kroon.food_recommend_back.repository;

import com.kroon.food_recommend_back.entity.MenuOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuOptionRepository extends JpaRepository<MenuOption, UUID> {

    List<MenuOption> findByOptionGroupIdInOrderBySortOrderAsc(List<UUID> optionGroupIds);

    void deleteByOptionGroupId(UUID optionGroupId);
}
