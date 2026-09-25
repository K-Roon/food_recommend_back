package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MenuOptionRequest {

    @NotBlank
    private String name;

    @NotNull
    @Min(0)
    private Integer extraPrice;

    private Integer sortOrder = 0;
}
