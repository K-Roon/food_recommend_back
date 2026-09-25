package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.dto.SanctionRequest;
import com.kroon.food_recommend_back.dto.UserSummaryResponse;
import com.kroon.food_recommend_back.service.CompanyAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** company_admin/admin 전용 — 권한 체크는 서비스 계층(CompanyAdminService)에서 합니다. */
@RestController
@RequestMapping("/api/company-admin/users")
@RequiredArgsConstructor
public class CompanyAdminController {

    private final CompanyAdminService companyAdminService;

    @GetMapping
    public List<UserSummaryResponse> list() {
        return companyAdminService.listCompanyUsers();
    }

    @PatchMapping("/{uid}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@PathVariable String uid, @Valid @RequestBody SanctionRequest request) {
        companyAdminService.suspend(uid, request);
    }

    @PatchMapping("/{uid}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable String uid) {
        companyAdminService.reactivate(uid);
    }

    /** 회사에서 제명 (soft delete + Firebase 계정 비활성화). */
    @DeleteMapping("/{uid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String uid, @Valid @RequestBody SanctionRequest request) {
        companyAdminService.removeFromCompany(uid, request);
    }
}
