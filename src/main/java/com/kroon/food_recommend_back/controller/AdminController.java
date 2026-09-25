package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.dto.RoleChangeRequest;
import com.kroon.food_recommend_back.dto.SanctionRequest;
import com.kroon.food_recommend_back.dto.UserSummaryResponse;
import com.kroon.food_recommend_back.service.CompanyAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** admin 전용 — 회사 무관 사용자 관리. 권한 체크는 서비스 계층에서 합니다. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final CompanyAdminService companyAdminService;

    @GetMapping
    public List<UserSummaryResponse> list(@RequestParam(required = false) UUID companyId) {
        return companyAdminService.listAllUsers(companyId);
    }

    @GetMapping("/{uid}")
    public UserSummaryResponse get(@PathVariable String uid) {
        return companyAdminService.getUser(uid);
    }

    /** 역할/소속 지정 (예: 지점 회사관리자 임명). */
    @PatchMapping("/{uid}/role")
    public UserSummaryResponse changeRole(@PathVariable String uid, @Valid @RequestBody RoleChangeRequest request) {
        return companyAdminService.changeRole(uid, request);
    }

    /** 앱 전체 이용 제한. */
    @PatchMapping("/{uid}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ban(@PathVariable String uid, @Valid @RequestBody SanctionRequest request) {
        companyAdminService.ban(uid, request);
    }

    /** 정지/밴 해제. */
    @PatchMapping("/{uid}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable String uid) {
        companyAdminService.reactivate(uid);
    }
}
