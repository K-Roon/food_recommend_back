package com.kroon.food_recommend_back.controller;

import com.kroon.food_recommend_back.keepalive.DbPingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 인증 없는 공개 엔드포인트 (SecurityConfig / FirebaseAuthenticationFilter 양쪽에서 열어둠).
 * 서버 기동 시 CloudSchedulerKeepAliveRegistrar가 자동 등록한 Cloud Scheduler 잡이
 * 주기적으로 이 엔드포인트를 호출합니다 — 사람이 따로 설정할 것은 없습니다.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DbPingService dbPingService;

    @GetMapping("/db-ping")
    public Map<String, Object> dbPing() {
        DbPingService.PingResult result = dbPingService.ping();
        return Map.of(
                "status", "ok",
                "checkedAt", result.checkedAt().toString(),
                "cached", result.cached()
        );
    }
}
