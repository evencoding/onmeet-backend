package com.onmeet.notification.infra;

import com.onmeet.notification.config.AuthServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * auth-service의 내부 API를 호출하여 사용자 정보를 조회합니다.
 */
@Slf4j
@Component
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    private final AuthServiceProperties authServiceProperties;
    private final String gatewaySharedSecret;

    public AuthServiceClient(RestTemplate restTemplate,
            AuthServiceProperties authServiceProperties,
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        this.restTemplate = restTemplate;
        this.authServiceProperties = authServiceProperties;
        this.gatewaySharedSecret = gatewaySharedSecret;
    }

    /**
     * 사용자 이름을 조회합니다. 실패 시 "알 수 없는 사용자"를 반환합니다.
     */
    public String getUserName(Long userId) {
        if (userId == null) {
            return "알 수 없는 사용자";
        }

        String url = authServiceProperties.getInternalUrl() + "/auth/internal/users/" + userId;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-Secret", gatewaySharedSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UserInfoResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, UserInfoResponse.class);

            UserInfoResponse body = response.getBody();
            return body != null && body.name() != null ? body.name() : "사용자 " + userId;
        } catch (Exception e) {
            log.warn("Failed to fetch user name for userId={}: {}", userId, e.getMessage());
            return "사용자 " + userId;
        }
    }

    /**
     * auth-service UserInfoDto 응답 매핑
     */
    public record UserInfoResponse(Long userId, String name, String email, Long profileImageId) {
    }
}
