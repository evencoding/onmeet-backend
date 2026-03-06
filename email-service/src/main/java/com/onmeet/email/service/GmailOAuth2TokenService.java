package com.onmeet.email.service;

import com.onmeet.email.config.GmailOAuth2Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GmailOAuth2TokenService {

    private static final Logger log = LoggerFactory.getLogger(GmailOAuth2TokenService.class);
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final GmailOAuth2Config config;
    private final RestTemplate restTemplate;

    // RestTemplateBuilder를 주입받아 Bean으로 생성된 RestTemplate 사용 (중앙 설정 관리, 재사용성 향상)
    public GmailOAuth2TokenService(GmailOAuth2Config config, RestTemplateBuilder restTemplateBuilder) {
        this.config = config;
        this.restTemplate = restTemplateBuilder.build();
    }

    public String getAccessToken() {
        if (config.getClientId() == null || config.getClientId().isBlank()) {
            log.warn("GMAIL_CLIENT_ID is missing or blank. Token cannot be refreshed.");
            throw new RuntimeException("Missing Gmail OAuth credentials");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", config.getClientId());
        params.add("client_secret", config.getClientSecret());
        params.add("refresh_token", config.getRefreshToken());
        params.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            // 타입 안전성을 위해 Map<String, Object>로 명시적 타입 파라미터 지정
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(TOKEN_URL, request, (Class<Map<String, Object>>) (Class<?>) Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            } else {
                log.error("Failed to refresh Gmail Access Token. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to refresh Gmail Access Token");
            }
        } catch (Exception e) {
            log.error("Error occurred while refreshing Gmail Access Token", e);
            throw new RuntimeException("Error occurred while refreshing Gmail Access Token", e);
        }
    }
}
