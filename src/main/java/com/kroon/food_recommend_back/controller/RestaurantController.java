package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.dto.DeleteRequest;
import com.kroon.food_recommend_back.dto.RestaurantRequest;
import com.kroon.food_recommend_back.dto.RestaurantResponse;
import com.kroon.food_recommend_back.service.RestaurantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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

    @GetMapping("/{restaurantId}")
    public RestaurantResponse get(@PathVariable UUID restaurantId) {
        return restaurantService.get(restaurantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantResponse create(@Valid @RequestBody RestaurantRequest request) {
        return restaurantService.create(request);
    }

    /** 수정 — 등록자 본인 / 회사관리자 / 관리자. */
    @PutMapping("/{restaurantId}")
    public RestaurantResponse update(@PathVariable UUID restaurantId, @Valid @RequestBody RestaurantRequest request) {
        return restaurantService.update(restaurantId, request);
    }

    /** 삭제 — 본인이면 본인삭제, 회사관리자/관리자가 남의 것을 지우면 규정위반 삭제(body.note 필수). 하위 메뉴도 같이 삭제. */
    @DeleteMapping("/{restaurantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID restaurantId, @Valid @RequestBody(required = false) DeleteRequest request) {
        restaurantService.delete(restaurantId, DeleteRequest.noteOf(request));
    }
}
