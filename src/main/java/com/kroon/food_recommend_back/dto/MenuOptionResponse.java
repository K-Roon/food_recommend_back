package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.MenuOption;
import lombok.Getter;

import java.util.UUID;

@Getter
public class MenuOptionResponse {

    private final UUID id;
    private final String name;
    private final Integer extraPrice;
    private final Integer sortOrder;

    public MenuOptionResponse(MenuOption option) {
        this.id = option.getId();
        this.name = option.getName();
        this.extraPrice = option.getExtraPrice();
        this.sortOrder = option.getSortOrder();
    }
}
