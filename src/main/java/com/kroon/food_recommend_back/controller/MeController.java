package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.dto.ChangeCompanyRequest;
import com.kroon.food_recommend_back.dto.MeResponse;
import com.kroon.food_recommend_back.service.MeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 본인 계정. 정지/밴/제명/탈퇴 계정은 403(ProblemDetail)을 받습니다.
 * 계정 생성은 POST /api/signup, 비밀번호/이메일 변경은 앱에서 Firebase Auth로 직접.
 */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @GetMapping("/api/me")
    public MeResponse me() {
        return meService.me();
    }

    /** 소속 변경 (사내 이동) — 새 지점 초대코드로. 일반 사용자만. */
    @PatchMapping("/api/me/company")
    public MeResponse changeCompany(@Valid @RequestBody ChangeCompanyRequest request) {
        return meService.changeCompany(request.inviteCode());
    }

    /** 회원 탈퇴 — 내 리뷰는 '탈퇴 삭제' 처리, Firebase 계정 삭제. */
    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw() {
        meService.withdraw();
    }
}
