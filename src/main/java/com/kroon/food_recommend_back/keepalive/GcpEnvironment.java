package com.kroon.food_recommend_back.keepalive;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

/**
 * Cloud Run 런타임 정보 조회 헬퍼.
 * - K_SERVICE 환경변수: Cloud Run이 모든 인스턴스에 자동으로 넣어주는 서비스 이름
 * - 메타데이터 서버: 프로젝트 ID/번호, 리전, 런타임 서비스 계정
 * - Application Default Credentials: Cloud Run에선 런타임 서비스 계정 토큰
 */
@Component
public class GcpEnvironment {

    private static final String METADATA_BASE = "http://metadata.google.internal/computeMetadata/v1/";
    private static final List<String> CLOUD_PLATFORM_SCOPE = List.of("https://www.googleapis.com/auth/cloud-platform");

    private final RestClient metadataClient;
    private volatile GoogleCredentials credentials;

    public GcpEnvironment() {
        this.metadataClient = RestClient.builder()
                .baseUrl(METADATA_BASE)
                .defaultHeader("Metadata-Flavor", "Google")
                .build();
    }

    public boolean isCloudRun() {
        return serviceName() != null;
    }

    public String serviceName() {
        String name = System.getenv("K_SERVICE");
        return (name == null || name.isBlank()) ? null : name;
    }

    public String projectId() {
        return metadata("project/project-id");
    }

    public String projectNumber() {
        return metadata("project/numeric-project-id");
    }

    /** 메타데이터 서버는 "projects/123/regions/asia-northeast3" 형태로 주므로 마지막 토막만 씁니다. */
    public String region() {
        String raw = metadata("instance/region");
        return raw.substring(raw.lastIndexOf('/') + 1);
    }

    public String accessToken() throws IOException {
        GoogleCredentials creds = credentials;
        if (creds == null) {
            creds = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE);
            credentials = creds;
        }
        creds.refreshIfExpired();
        return creds.getAccessToken().getTokenValue();
    }

    private String metadata(String path) {
        String value = metadataClient.get().uri(path).retrieve().body(String.class);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("메타데이터 서버 응답이 비어 있습니다: " + path);
        }
        return value.trim();
    }
}
