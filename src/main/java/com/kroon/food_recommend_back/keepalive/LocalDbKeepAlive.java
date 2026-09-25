package com.kroon.food_recommend_back.keepalive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cloud Run이 아닌 곳(로컬, 항상 켜져 있는 VM 등)에서 서버가 떠 있는 동안
 * 6시간마다 DB를 건드립니다. Cloud Run에서는 CloudSchedulerKeepAliveRegistrar가
 * 등록한 외부 스케줄러가 이 역할을 하므로 여기서는 건너뜁니다.
 */
@Component
public class LocalDbKeepAlive {

    private static final Logger log = LoggerFactory.getLogger(LocalDbKeepAlive.class);

    private final KeepAliveProperties props;
    private final GcpEnvironment gcp;
    private final DbPingService dbPingService;

    public LocalDbKeepAlive(KeepAliveProperties props, GcpEnvironment gcp, DbPingService dbPingService) {
        this.props = props;
        this.gcp = gcp;
        this.dbPingService = dbPingService;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT6H")
    public void ping() {
        if (!props.enabled() || gcp.isCloudRun()) {
            return;
        }
        try {
            dbPingService.ping();
            log.debug("[keep-alive] 로컬 DB ping 완료");
        } catch (Exception e) {
            log.warn("[keep-alive] 로컬 DB ping 실패: {}", e.toString());
        }
    }
}
