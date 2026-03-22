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

import java.util.Collections;
import java.util.List;

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

    public String getUserName(Long userId) {
        UserInfoResponse userInfo = getUserInfo(userId);
        return userInfo != null && userInfo.name() != null ? userInfo.name() : "사용자 " + userId;
    }

    /**
     * 사용자 정보를 상세히 조회합니다.
     */
    public UserInfoResponse getUserInfo(Long userId) {
        if (userId == null) {
            return null;
        }

        String url = authServiceProperties.getInternalUrl() + "/auth/internal/users/" + userId;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-Secret", gatewaySharedSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UserInfoResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, UserInfoResponse.class);

            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch user info for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 여러 사용자의 정보를 일괄 조회합니다.
     */
    public List<UserInfoResponse> getBatchUserInfo(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        String url = authServiceProperties.getInternalUrl() + "/auth/internal/users/batch";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-Secret", gatewaySharedSecret);
            BatchUserInfoRequest request = new BatchUserInfoRequest(userIds);
            HttpEntity<BatchUserInfoRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<BatchUserInfoResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, BatchUserInfoResponse.class);

            BatchUserInfoResponse body = response.getBody();
            return body != null ? body.users() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Failed to fetch batch user info: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * auth-service UserInfoDto 응답 매핑
     */
    public record UserInfoResponse(Long userId, String name, String email, Long profileImageId, String fcmDeviceToken) {
    }

    public record BatchUserInfoRequest(List<Long> userIds) {
    }

    public record BatchUserInfoResponse(List<UserInfoResponse> users) {
    }
}
