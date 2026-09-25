package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.dto.CompanyAdminRequest;
import com.kroon.food_recommend_back.dto.CompanyResponse;
import com.kroon.food_recommend_back.dto.CompanySettingsRequest;
import com.kroon.food_recommend_back.service.CompanyService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    // ------------------------------------------------------------- 내 회사

    @GetMapping("/api/company")
    public CompanyResponse myCompany() {
        return companyService.myCompany();
    }

    /** 회사관리자 — 메뉴 등록 시 승인 필요 여부 등 */
    @PatchMapping("/api/company/settings")
    public CompanyResponse updateSettings(@Valid @RequestBody CompanySettingsRequest request) {
        return companyService.updateMySettings(request);
    }

    // ------------------------------------------------------------- 관리자

    @GetMapping("/api/admin/companies")
    public List<CompanyResponse> list() {
        return companyService.listAll();
    }

    @GetMapping("/api/admin/companies/{companyId}")
    public CompanyResponse get(@PathVariable UUID companyId) {
        return companyService.get(companyId);
    }

    @PostMapping("/api/admin/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CompanyAdminRequest request) {
        return companyService.create(request);
    }

    @PutMapping("/api/admin/companies/{companyId}")
    public CompanyResponse update(@PathVariable UUID companyId, @Valid @RequestBody CompanyAdminRequest request) {
        return companyService.update(companyId, request);
    }

    @DeleteMapping("/api/admin/companies/{companyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID companyId) {
        companyService.delete(companyId);
    }
}
