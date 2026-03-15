package com.onmeet.video.infra.auth;

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
            // CHECK [auth-담당자]: "Unknown User" 더미 폴백 제거 → 예외 전파로 변경.
            // auth-service 장애 시 상위 레이어가 적절한 에러 처리를 하도록 예외를 그대로 던짐.
            logger.error("Failed to fetch user info from auth-service for userId: {}", userId, e);
            throw new RuntimeException("auth-service 호출 실패: userId=" + userId, e);
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
            // CHECK [auth-담당자]: batch "Unknown User" 더미 폴백 제거 → 예외 전파로 변경.
            // auth-service 장애 시 상위 레이어가 적절한 에러 처리를 하도록 예외를 그대로 던짐.
            logger.error("Failed to fetch batch user info from auth-service for userIds: {}", userIds, e);
            throw new RuntimeException("auth-service 배치 호출 실패: userIds=" + userIds, e);
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
            Map<Long, Boolean> result = new HashMap<>();
            for (Long userId : userIds) {
                result.put(userId, false);
            }
            return result;
        }
    }

    @Override
    public boolean teamExists(Long teamId) {
        String url = authServiceProperties.getInternalUrl() + "/auth/internal/teams/" + teamId + "/exists";

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<TeamExistsResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    TeamExistsResponse.class
            );

            TeamExistsResponse body = response.getBody();
            return body != null && body.exists();
        } catch (Exception e) {
            logger.error("Failed to check if team exists for teamId: {}", teamId, e);
            return false;
        }
    }

    @Override
    public boolean isTeamMember(Long teamId, Long userId) {
        String url = authServiceProperties.getInternalUrl() + "/auth/internal/teams/membership/check";

        try {
            HttpHeaders headers = createHeaders();
            TeamMembershipRequest request = new TeamMembershipRequest(teamId, userId);
            HttpEntity<TeamMembershipRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<TeamMembershipResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    TeamMembershipResponse.class
            );

            TeamMembershipResponse body = response.getBody();
            return body != null && body.isMember();
        } catch (Exception e) {
            logger.error("Failed to check team membership for teamId: {}, userId: {}", teamId, userId, e);
            return false;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Gateway-Secret", gatewaySharedSecret);
        return headers;
    }
}
