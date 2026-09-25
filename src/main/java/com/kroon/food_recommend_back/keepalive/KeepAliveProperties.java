package com.kroon.food_recommend_back.keepalive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Supabase 무료 티어 휴면(일정 기간 요청 없으면 프로젝트 일시정지) 방지 설정.
 * 전부 기본값이 있어서 application.yml에 아무것도 안 적어도 동작합니다.
 *
 * @param enabled           keep-alive 전체 on/off
 * @param jobName           Cloud Scheduler 잡 이름
 * @param schedule          Cloud Scheduler cron (기본: 3일마다 09:00 — 휴면 기준 1주일 대비 여유)
 * @param timeZone          cron 기준 타임존
 * @param schedulerLocation Cloud Scheduler 리전 (비우면 Cloud Run 리전과 동일)
 * @param baseUrl           핑 대상 서버 URL (비우면 Cloud Run Admin API로 자동 조회)
 */
@ConfigurationProperties(prefix = "app.keepalive")
public record KeepAliveProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("db-keepalive") String jobName,
        @DefaultValue("0 9 */3 * *") String schedule,
        @DefaultValue("Asia/Seoul") String timeZone,
        String schedulerLocation,
        String baseUrl
) {
}
