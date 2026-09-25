package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SanctionRequest {

    @NotBlank
    private String reason;
}
