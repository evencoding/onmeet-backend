package com.onmeet.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.config-path:firebase/serviceAccountKey.json}")
    private String firebaseConfigPath;

    @Value("${firebase.credentials-json:}")
    private String credentialsJson;

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(resolveCredentials());

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }
        } catch (IOException e) {
            log.warn("Firebase credentials not found. FCM push will be disabled. " +
                    "Set FIREBASE_CREDENTIALS_JSON env or place serviceAccountKey.json in classpath.");
        }
    }

    private InputStream resolveCredentials() throws IOException {
        // 1순위: 환경변수로 주입된 JSON 문자열
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            log.info("Firebase credentials loaded from environment variable");
            return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }

        // 2순위: 외부 파일 경로 (GOOGLE_APPLICATION_CREDENTIALS 등)
        Path externalPath = Path.of(firebaseConfigPath);
        if (Files.exists(externalPath)) {
            log.info("Firebase credentials loaded from file: {}", firebaseConfigPath);
            return Files.newInputStream(externalPath);
        }

        // 3순위: classpath 리소스
        log.info("Firebase credentials loaded from classpath: {}", firebaseConfigPath);
        return new ClassPathResource(firebaseConfigPath).getInputStream();
    }
}
