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
@Table(name = "menu_options")
@Getter
@Setter
@NoArgsConstructor
public class MenuOption {

    @Id
    private UUID id;

    @Column(name = "option_group_id", nullable = false)
    private UUID optionGroupId;

    private String name;

    @Column(name = "extra_price", nullable = false)
    private Integer extraPrice;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
