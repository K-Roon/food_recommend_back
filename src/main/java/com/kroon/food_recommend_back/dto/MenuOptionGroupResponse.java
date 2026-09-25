package com.kroon.food_recommend_back.dto;

import com.kroon.food_recommend_back.entity.MenuOptionGroup;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class MenuOptionGroupResponse {

    private final UUID id;
    private final String title;
    private final boolean multiSelect;
    private final boolean required;
    private final Integer sortOrder;
    private final List<MenuOptionResponse> options;

    public MenuOptionGroupResponse(MenuOptionGroup group, List<MenuOptionResponse> options) {
        this.id = group.getId();
        this.title = group.getTitle();
        this.multiSelect = group.isMultiSelect();
        this.required = group.isRequired();
        this.sortOrder = group.getSortOrder();
        this.options = options;
    }
}
