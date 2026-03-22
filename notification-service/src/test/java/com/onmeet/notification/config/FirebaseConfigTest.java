package com.onmeet.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FirebaseConfigTest {

    @AfterEach
    void tearDown() {
        // 테스트 간 FirebaseApp 충돌 방지
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    @Test
    @DisplayName("classpath의 serviceAccountKey.json으로 Firebase 초기화 성공")
    void initFromClasspath() throws IOException {
        ClassPathResource resource = new ClassPathResource("firebase/serviceAccountKey.json");

        assertTrue(resource.exists(), "serviceAccountKey.json이 classpath에 존재해야 합니다");

        GoogleCredentials credentials = GoogleCredentials.fromStream(resource.getInputStream());
        assertNotNull(credentials, "GoogleCredentials 생성 성공");

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        assertNotNull(app, "FirebaseApp 초기화 성공");
        assertFalse(FirebaseApp.getApps().isEmpty(), "FirebaseApp이 등록되어 있어야 합니다");

        System.out.println("Firebase initialized: projectId=" + options.getProjectId());
    }

    @Test
    @DisplayName("JSON 문자열로 Firebase 초기화 성공 (환경변수 시뮬레이션)")
    void initFromJsonString() throws IOException {
        // classpath에서 JSON 읽어서 문자열로 변환 (환경변수 주입 시뮬레이션)
        ClassPathResource resource = new ClassPathResource("firebase/serviceAccountKey.json");
        if (!resource.exists()) {
            System.out.println("serviceAccountKey.json 없음 - 스킵");
            return;
        }

        String json = Files.readString(Path.of(resource.getURI()));

        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertNotNull(credentials, "JSON 문자열에서 GoogleCredentials 생성 성공");

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        assertNotNull(app, "FirebaseApp 초기화 성공 (JSON 문자열)");

        System.out.println("Firebase initialized from JSON string: OK");
    }
}
