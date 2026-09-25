package com.kroon.food_recommend_back.keepalive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 서버가 스스로 Cloud Scheduler 잡을 등록하는 keep-alive.
 *
 * 왜 앱 안의 @Scheduled만으로는 안 되나:
 *   Cloud Run은 요청이 없으면 인스턴스를 0개로 줄입니다. keep-alive가 정말 필요한
 *   순간(= 아무도 앱을 안 쓰는 며칠)에는 인스턴스 자체가 없어서 앱 안의 타이머도
 *   같이 멈춥니다. 그래서 "밖에서 깨워주는" Cloud Scheduler가 필요합니다.
 *
 * 이 클래스가 하는 일 (사람이 손댈 것 없음):
 *   1. 서버가 Cloud Run에서 뜨면(ApplicationReadyEvent) 백그라운드 스레드에서
 *   2. 메타데이터 서버로 프로젝트/리전, Cloud Run Admin API로 자기 URL을 알아내고
 *   3. Cloud Scheduler에 "N일마다 GET {URL}/api/health/db-ping" 잡이 있는지 확인해서
 *      없으면 만들고, 설정이 바뀌었으면 갱신합니다 (멱등 — 인스턴스가 여러 개 떠도 안전).
 *   4. Cloud Scheduler API가 꺼져 있으면 Service Usage API로 켜고, 1시간 뒤 재시도합니다.
 *
 * 필요한 권한: Cloud Run 런타임 서비스 계정에 cloudscheduler.jobs.*, run.services.get,
 * serviceusage.services.enable. 기본 Compute 서비스 계정(편집자 역할)이면 이미 다 있습니다.
 * 별도 서비스 계정을 쓰면 roles/cloudscheduler.admin, roles/run.viewer,
 * roles/serviceusage.serviceUsageAdmin을 한 번 붙여주면 됩니다 — 실패 시 로그에 안내가 찍힙니다.
 *
 * 로컬(K_SERVICE 없음)에서는 아무것도 하지 않고, 대신 LocalDbKeepAlive가 동작합니다.
 */
@Component
public class CloudSchedulerKeepAliveRegistrar {

    private static final Logger log = LoggerFactory.getLogger(CloudSchedulerKeepAliveRegistrar.class);

    static final String PING_PATH = "/api/health/db-ping";
    private final String schedulerApi;
    private final String runApi;
    private final String serviceUsageApi;

    private final KeepAliveProperties props;
    private final GcpEnvironment gcp;
    private final RestClient http;

    private volatile boolean registered = false;

    @Autowired
    public CloudSchedulerKeepAliveRegistrar(KeepAliveProperties props, GcpEnvironment gcp) {
        this(props, gcp, "https://cloudscheduler.googleapis.com/v1/",
                "https://run.googleapis.com/v2/", "https://serviceusage.googleapis.com/v1/");
    }

    /** 테스트에서 가짜 GCP API 서버를 붙이기 위한 생성자. */
    CloudSchedulerKeepAliveRegistrar(KeepAliveProperties props, GcpEnvironment gcp,
                                     String schedulerApi, String runApi, String serviceUsageApi) {
        this.props = props;
        this.gcp = gcp;
        this.http = RestClient.create();
        this.schedulerApi = schedulerApi;
        this.runApi = runApi;
        this.serviceUsageApi = serviceUsageApi;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!shouldRun()) {
            return;
        }
        // 기동 시간(=Cloud Run 콜드스타트)을 늘리지 않도록 백그라운드에서 처리
        Thread thread = new Thread(this::ensureJob, "keepalive-registrar");
        thread.setDaemon(true);
        thread.start();
    }

    /** 첫 시도가 실패했을 때(권한 전파 지연, API 활성화 대기 등) 인스턴스가 살아있는 동안 재시도. */
    @Scheduled(initialDelayString = "PT1H", fixedDelayString = "PT1H")
    public void retryIfNeeded() {
        if (shouldRun() && !registered) {
            ensureJob();
        }
    }

    private boolean shouldRun() {
        return props.enabled() && gcp.isCloudRun();
    }

    synchronized void ensureJob() {
        if (registered) {
            return;
        }
        String projectNumber = null;
        try {
            String projectId = gcp.projectId();
            projectNumber = gcp.projectNumber();
            String region = gcp.region();
            String location = hasText(props.schedulerLocation()) ? props.schedulerLocation() : region;
            String targetUri = resolveBaseUrl(projectId, projectNumber, region) + PING_PATH;

            String parent = "projects/" + projectId + "/locations/" + location;
            String jobName = parent + "/jobs/" + props.jobName();
            Map<String, Object> desired = desiredJob(jobName, targetUri);

            Map<?, ?> existing = getJob(jobName);
            if (existing == null) {
                createJob(parent, desired);
                log.info("[keep-alive] Cloud Scheduler 잡 생성 완료: {} → {} ({} {})",
                        jobName, targetUri, props.schedule(), props.timeZone());
            } else if (!sameConfig(existing, targetUri)) {
                updateJob(jobName, desired);
                log.info("[keep-alive] Cloud Scheduler 잡 설정 갱신: {} → {}", jobName, targetUri);
            } else {
                log.info("[keep-alive] Cloud Scheduler 잡이 이미 최신 상태입니다: {}", jobName);
            }
            registered = true;
        } catch (RestClientResponseException e) {
            handleApiError(e, projectNumber);
        } catch (Exception e) {
            log.warn("[keep-alive] Cloud Scheduler 잡 등록 실패 (1시간 뒤 재시도): {}", e.toString());
        }
    }

    private String resolveBaseUrl(String projectId, String projectNumber, String region) throws Exception {
        if (hasText(props.baseUrl())) {
            return stripTrailingSlash(props.baseUrl());
        }
        String service = gcp.serviceName();
        try {
            Map<?, ?> svc = http.get()
                    .uri(runApi + "projects/{p}/locations/{r}/services/{s}", projectId, region, service)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .body(Map.class);
            Object uri = svc == null ? null : svc.get("uri");
            if (uri instanceof String s && hasText(s)) {
                return stripTrailingSlash(s);
            }
        } catch (RestClientResponseException e) {
            log.info("[keep-alive] Cloud Run Admin API로 URL 조회 실패({}), 결정적 URL 형식으로 대체합니다.",
                    e.getStatusCode());
        }
        // Cloud Run 결정적 URL: https://SERVICE-PROJECT_NUMBER.REGION.run.app
        return "https://" + service + "-" + projectNumber + "." + region + ".run.app";
    }

    private Map<String, Object> desiredJob(String jobName, String targetUri) {
        Map<String, Object> httpTarget = new LinkedHashMap<>();
        httpTarget.put("uri", targetUri);
        httpTarget.put("httpMethod", "GET");

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("name", jobName);
        job.put("description", "Supabase 무료 티어 휴면 방지용 DB keep-alive (서버가 자동 등록)");
        job.put("schedule", props.schedule());
        job.put("timeZone", props.timeZone());
        job.put("httpTarget", httpTarget);
        job.put("retryConfig", Map.of("retryCount", 3));
        return job;
    }

    private boolean sameConfig(Map<?, ?> existing, String targetUri) {
        Object target = existing.get("httpTarget");
        Object uri = (target instanceof Map<?, ?> t) ? t.get("uri") : null;
        return Objects.equals(existing.get("schedule"), props.schedule())
                && Objects.equals(existing.get("timeZone"), props.timeZone())
                && Objects.equals(uri, targetUri);
    }

    private Map<?, ?> getJob(String jobName) throws Exception {
        try {
            return http.get()
                    .uri(schedulerApi + jobName)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    private void createJob(String parent, Map<String, Object> job) throws Exception {
        try {
            http.post()
                    .uri(schedulerApi + parent + "/jobs")
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(job)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            // 다른 인스턴스가 동시에 먼저 만든 경우 — 목적은 달성됐으므로 성공으로 취급
            log.info("[keep-alive] 다른 인스턴스가 이미 잡을 생성했습니다.");
        }
    }

    private void updateJob(String jobName, Map<String, Object> job) throws Exception {
        http.patch()
                .uri(schedulerApi + jobName + "?updateMask=description,schedule,timeZone,httpTarget,retryConfig")
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(job)
                .retrieve()
                .toBodilessEntity();
    }

    private void handleApiError(RestClientResponseException e, String projectNumber) {
        String body = e.getResponseBodyAsString();
        if (body.contains("SERVICE_DISABLED") || body.contains("has not been used")) {
            log.warn("[keep-alive] Cloud Scheduler API가 꺼져 있어 활성화를 요청합니다 (1시간 뒤 재시도).");
            enableSchedulerApi(projectNumber);
        } else if (e.getStatusCode().value() == 403) {
            log.warn("[keep-alive] 권한 부족으로 Cloud Scheduler 잡을 만들지 못했습니다. Cloud Run 런타임 서비스 계정에 "
                    + "roles/cloudscheduler.admin, roles/run.viewer 를 부여해 주세요. 응답: {}", body);
        } else {
            log.warn("[keep-alive] Cloud Scheduler API 오류 {} (1시간 뒤 재시도): {}", e.getStatusCode(), body);
        }
    }

    private void enableSchedulerApi(String projectNumber) {
        if (projectNumber == null) {
            return;
        }
        try {
            http.post()
                    .uri(serviceUsageApi + "projects/{n}/services/cloudscheduler.googleapis.com:enable", projectNumber)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("[keep-alive] Cloud Scheduler API 자동 활성화 실패 — 콘솔에서 한 번만 켜 주세요: {}", ex.toString());
        }
    }

    private String token() {
        try {
            return gcp.accessToken();
        } catch (Exception e) {
            throw new IllegalStateException("GCP 액세스 토큰 발급 실패", e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
