package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.dto.DeleteRequest;
import com.kroon.food_recommend_back.dto.MenuOptionGroupRequest;
import com.kroon.food_recommend_back.dto.MenuOptionGroupResponse;
import com.kroon.food_recommend_back.dto.MenuOptionGroupUpdateRequest;
import com.kroon.food_recommend_back.dto.MenuOptionRequest;
import com.kroon.food_recommend_back.dto.MenuOptionResponse;
import com.kroon.food_recommend_back.dto.MenuRequest;
import com.kroon.food_recommend_back.dto.MenuResponse;
import com.kroon.food_recommend_back.dto.MenuSummaryResponse;
import com.kroon.food_recommend_back.dto.MenuUpdateRequest;
import com.kroon.food_recommend_back.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // ---------------------------------------------------------------- 메뉴

    /** 기본 목록 뷰 — 레스토랑이 아니라 메뉴 아이템 단위로 보여줍니다. */
    @GetMapping
    public List<MenuSummaryResponse> list(@RequestParam(required = false) UUID restaurantId) {
        return menuService.list(restaurantId);
    }

    /** 내가 등록한 메뉴 (승인 대기/반려 포함). */
    @GetMapping("/mine")
    public List<MenuSummaryResponse> mine() {
        return menuService.listMine();
    }

    /** 승인 대기 목록 — company_admin/admin 전용. */
    @GetMapping("/pending")
    public List<MenuSummaryResponse> pending() {
        return menuService.listPending();
    }

    /** 메뉴 탭 → 레스토랑 위치/평점 + 옵션 그룹까지 포함한 상세. */
    @GetMapping("/{menuId}")
    public MenuResponse get(@PathVariable UUID menuId) {
        return menuService.get(menuId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuResponse create(@Valid @RequestBody MenuRequest request) {
        return menuService.create(request);
    }

    /** 기본 정보 수정. 승인 필요 회사에서 일반 사용자가 수정하면 재승인 대기로 돌아감. */
    @PutMapping("/{menuId}")
    public MenuResponse update(@PathVariable UUID menuId, @Valid @RequestBody MenuUpdateRequest request) {
        return menuService.update(menuId, request);
    }

    /** 본인 삭제 / 규정위반 삭제(회사관리자·관리자, body.note 필수). */
    @DeleteMapping("/{menuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID menuId, @Valid @RequestBody(required = false) DeleteRequest request) {
        menuService.delete(menuId, DeleteRequest.noteOf(request));
    }

    @PatchMapping("/{menuId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable UUID menuId) {
        menuService.approve(menuId);
    }

    @PatchMapping("/{menuId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID menuId) {
        menuService.reject(menuId);
    }

    // ---------------------------------------------------------------- 옵션 그룹

    @PostMapping("/{menuId}/option-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuOptionGroupResponse addOptionGroup(@PathVariable UUID menuId,
                                                  @Valid @RequestBody MenuOptionGroupRequest request) {
        return menuService.addOptionGroup(menuId, request);
    }

    @PutMapping("/{menuId}/option-groups/{groupId}")
    public MenuOptionGroupResponse updateOptionGroup(@PathVariable UUID menuId, @PathVariable UUID groupId,
                                                     @Valid @RequestBody MenuOptionGroupUpdateRequest request) {
        return menuService.updateOptionGroup(menuId, groupId, request);
    }

    @DeleteMapping("/{menuId}/option-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOptionGroup(@PathVariable UUID menuId, @PathVariable UUID groupId) {
        menuService.deleteOptionGroup(menuId, groupId);
    }

    // ---------------------------------------------------------------- 옵션

    @PostMapping("/{menuId}/option-groups/{groupId}/options")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuOptionResponse addOption(@PathVariable UUID menuId, @PathVariable UUID groupId,
                                        @Valid @RequestBody MenuOptionRequest request) {
        return menuService.addOption(menuId, groupId, request);
    }

    @PutMapping("/{menuId}/option-groups/{groupId}/options/{optionId}")
    public MenuOptionResponse updateOption(@PathVariable UUID menuId, @PathVariable UUID groupId,
                                           @PathVariable UUID optionId, @Valid @RequestBody MenuOptionRequest request) {
        return menuService.updateOption(menuId, groupId, optionId, request);
    }

    @DeleteMapping("/{menuId}/option-groups/{groupId}/options/{optionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOption(@PathVariable UUID menuId, @PathVariable UUID groupId, @PathVariable UUID optionId) {
        menuService.deleteOption(menuId, groupId, optionId);
    }
}
