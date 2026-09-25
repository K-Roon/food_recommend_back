package com.kroon.food_recommend_back.keepalive;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * DB에 실제 쿼리를 한 번 날려서 Supabase 쪽 "활동" 기록을 남깁니다.
 * /api/health/db-ping은 인증 없는 공개 엔드포인트라, 누가 연타해도 DB까지는
 * 60초에 한 번만 가도록 스로틀링합니다.
 */
@Service
public class DbPingService {

    private static final Duration THROTTLE = Duration.ofSeconds(60);

    private final JdbcTemplate jdbcTemplate;
    private volatile Instant lastPingAt;

    public DbPingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PingResult ping() {
        Instant now = Instant.now();
        Instant last = lastPingAt;
        if (last != null && Duration.between(last, now).compareTo(THROTTLE) < 0) {
            return new PingResult(last, true);
        }
        // select 1만 날리는 것보다 실제 테이블을 한 번 건드리는 쪽이 "활동"으로 확실히 잡힙니다.
        jdbcTemplate.queryForObject("select count(*) from (select 1 from companies limit 1) t", Integer.class);
        lastPingAt = now;
        return new PingResult(now, false);
    }

    public record PingResult(Instant checkedAt, boolean cached) {
    }
}
