package com.kroon.food_recommend_back.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReviewItemRequest(
        @NotNull UUID menuId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 1000) String comment
) {}
