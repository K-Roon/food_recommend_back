package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.entity.User;
import com.kroon.food_recommend_back.service.RlsContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 + RLS 부트스트랩이 제대로 도는지 확인하는 테스트용 엔드포인트.
 * 정상 응답이 오면 = 토큰 검증, 세션 컨텍스트 설정, RLS 정책까지 전부 살아있다는 뜻.
 */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final RlsContextService rlsContextService;

    @GetMapping("/api/me")
    public User me() {
        String uid = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return rlsContextService.bootstrap(uid);
    }
}
