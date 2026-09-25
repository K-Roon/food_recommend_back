package com.kroon.food_recommend_back.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class MenuOptionGroupRequest {

    @NotBlank
    private String title;

    private boolean multiSelect;

    private boolean required;

    private Integer sortOrder = 0;

    @NotEmpty
    @Valid
    private List<MenuOptionRequest> options;
}
