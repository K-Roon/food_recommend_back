package com.kroon.food_recommend_back.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "menu_option_groups")
@Getter
@Setter
@NoArgsConstructor
public class MenuOptionGroup {

    @Id
    private UUID id;

    @Column(name = "menu_id", nullable = false)
    private UUID menuId;

    private String title;

    @Column(name = "is_multi_select", nullable = false)
    private boolean multiSelect;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
