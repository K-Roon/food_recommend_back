package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.dto.RestaurantRequest;
import com.kroon.food_recommend_back.dto.RestaurantResponse;
import com.kroon.food_recommend_back.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping
    public List<RestaurantResponse> list() {
        return restaurantService.list();
    }

    /**
     * 등록 폼 제출 "전에" 프론트엔드가 먼저 호출해야 하는 중복 체크 API.
     * 예: GET /api/restaurants/search?name=김밥천국&address=강남대로
     */
    @GetMapping("/search")
    public List<RestaurantResponse> search(@RequestParam String name, @RequestParam String address) {
        return restaurantService.searchDuplicates(name, address);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantResponse create(@Valid @RequestBody RestaurantRequest request) {
        return restaurantService.create(request);
    }
}