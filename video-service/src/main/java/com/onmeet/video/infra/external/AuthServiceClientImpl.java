package com.onmeet.video.infra.external;

import com.onmeet.video.config.AuthServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AuthServiceClientImpl implements AuthServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceClientImpl.class);

    private final RestTemplate restTemplate;
    private final AuthServiceProperties authServiceProperties;
    private final String gatewaySharedSecret;

    public AuthServiceClientImpl(
            RestTemplate restTemplate,
            AuthServiceProperties authServiceProperties,
            @Value("${gateway.shared-secret}") String gatewaySharedSecret
    ) {
        this.restTemplate = restTemplate;
        this.authServiceProperties = authServiceProperties;
        this.gatewaySharedSecret = gatewaySharedSecret;
    }

    @Override
    public UserInfo getUserInfo(Long userId) {
        String url = authServiceProperties.getInternalUrl() + "/auth/internal/users/" + userId;

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UserInfo> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserInfo.class
            );

            return response.getBody();
        } catch (Exception e) {
            logger.error("Failed to fetch user info for userId: {}", userId, e);
            // Return fallback user info
            return new UserInfo(userId, "Unknown User", "unknown@example.com", null);
        }
    }

    @Override
    public List<UserInfo> getBatchUserInfo(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        String url = authServiceProperties.getInternalUrl() + "/auth/internal/users/batch";

        try {
            HttpHeaders headers = createHeaders();
            BatchUserInfoRequest request = new BatchUserInfoRequest(userIds);
            HttpEntity<BatchUserInfoRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<BatchUserInfoResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    BatchUserInfoResponse.class
            );

            BatchUserInfoResponse body = response.getBody();
            return body != null ? body.users() : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Failed to fetch batch user info for userIds: {}", userIds, e);
            // Return fallback user info list
            return userIds.stream()
                    .map(id -> new UserInfo(id, "Unknown User", "unknown@example.com", null))
                    .toList();
        }
    }

    @Override
    public boolean userExists(Long userId) {
        String url = authServiceProperties.getInternalUrl() + "/auth/internal/users/" + userId + "/exists";

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<UserExistsResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserExistsResponse.class
            );

            UserExistsResponse body = response.getBody();
            return body != null && body.exists();
        } catch (Exception e) {
            logger.error("Failed to check if user exists for userId: {}", userId, e);
            return false;
        }
    }

    @Override
    public Map<Long, Boolean> batchUserExists(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String url = authServiceProperties.getInternalUrl() + "/auth/internal/users/exists/batch";

        try {
            HttpHeaders headers = createHeaders();
            BatchUserExistsRequest request = new BatchUserExistsRequest(userIds);
            HttpEntity<BatchUserExistsRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<BatchUserExistsResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    BatchUserExistsResponse.class
            );

            BatchUserExistsResponse body = response.getBody();
            if (body != null && body.users() != null) {
                return body.users().stream()
                        .collect(Collectors.toMap(
                                UserExistsResponse::userId,
                                UserExistsResponse::exists
                        ));
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            logger.error("Failed to check batch user exists for userIds: {}", userIds, e);
            // Return all false
            Map<Long, Boolean> result = new HashMap<>();
            for (Long userId : userIds) {
                result.put(userId, false);
            }
            return result;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Gateway-Secret", gatewaySharedSecret);
        return headers;
    }
}
