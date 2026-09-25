package com.kroon.food_recommend_back.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class MenuRequest {

    @NotNull
    private UUID restaurantId;

    @NotBlank
    private String name;

    @NotNull
    @Min(0)
    private Integer price;

    private String imageUrl;

    private String imageSource;

    /** 메뉴 생성과 동시에 옵션 그룹까지 같이 등록할 때 사용 (선택). */
    @Valid
    private List<MenuOptionGroupRequest> optionGroups;
}
