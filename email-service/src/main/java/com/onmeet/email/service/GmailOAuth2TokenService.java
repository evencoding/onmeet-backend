package com.onmeet.email.service;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.EmailErrorCode;
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
    // Tokens expire in 60 min; refresh at 55 min to avoid expiry races
    private static final long TOKEN_TTL_MS = 55 * 60 * 1000L;

    private final GmailOAuth2Config config;
    private final RestTemplate restTemplate;

    private volatile String cachedToken = null;
    private volatile long tokenExpiryMs = 0L;

    public GmailOAuth2TokenService(GmailOAuth2Config config, RestTemplateBuilder restTemplateBuilder) {
        this.config = config;
        this.restTemplate = restTemplateBuilder.build();
    }

    public String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiryMs) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && System.currentTimeMillis() < tokenExpiryMs) {
                return cachedToken;
            }
            cachedToken = refreshAccessToken();
            tokenExpiryMs = System.currentTimeMillis() + TOKEN_TTL_MS;
            return cachedToken;
        }
    }

    private String refreshAccessToken() {
        if (config.getClientId() == null || config.getClientId().isBlank()) {
            log.warn("GMAIL_CLIENT_ID is missing or blank. Token cannot be refreshed.");
            throw new BusinessException(EmailErrorCode.CREDENTIALS_MISSING);
        }
        if (config.getClientSecret() == null || config.getClientSecret().isBlank()) {
            log.warn("GMAIL_CLIENT_SECRET is missing or blank. Token cannot be refreshed.");
            throw new BusinessException(EmailErrorCode.CREDENTIALS_MISSING);
        }
        if (config.getRefreshToken() == null || config.getRefreshToken().isBlank()) {
            log.warn("GMAIL_REFRESH_TOKEN is missing or blank. Token cannot be refreshed.");
            throw new BusinessException(EmailErrorCode.CREDENTIALS_MISSING);
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
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(TOKEN_URL, request, (Class<Map<String, Object>>) (Class<?>) Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String accessToken = (String) response.getBody().get("access_token");
                if (accessToken == null) {
                    throw new BusinessException(EmailErrorCode.TOKEN_RESPONSE_INVALID);
                }
                return accessToken;
            } else {
                throw new BusinessException(EmailErrorCode.TOKEN_REFRESH_FAILED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(EmailErrorCode.TOKEN_NETWORK_ERROR);
        }
    }
}
