package com.kroon.food_recommend_back.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Firebase Admin SDK 초기화.
 * 서비스 계정 JSON 파일을 그대로 커밋하지 않고, base64로 인코딩한 값을
 * 환경변수(FIREBASE_SERVICE_ACCOUNT_BASE64)로 주입받습니다.
 * Cloud Run 배포 시에는 이 값을 Secret Manager에 넣고 환경변수로 연결하세요.
 */
@Component
public class FirebaseConfig {

    @Value("${firebase.service-account-base64}")
    private String serviceAccountBase64;

    @PostConstruct
    public void init() throws Exception {
        if (FirebaseApp.getApps().isEmpty()) {
            byte[] decoded = Base64.getDecoder().decode(serviceAccountBase64);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(decoded)))
                    .build();
            FirebaseApp.initializeApp(options);
        }
    }
}
