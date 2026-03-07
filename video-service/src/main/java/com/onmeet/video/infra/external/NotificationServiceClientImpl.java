package com.onmeet.video.infra.external;

import com.onmeet.video.config.NotificationServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class NotificationServiceClientImpl implements NotificationServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceClientImpl.class);

    private final RestTemplate restTemplate;
    private final NotificationServiceProperties notificationServiceProperties;
    private final String gatewaySharedSecret;

    public NotificationServiceClientImpl(
            RestTemplate restTemplate,
            NotificationServiceProperties notificationServiceProperties,
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        this.restTemplate = restTemplate;
        this.notificationServiceProperties = notificationServiceProperties;
        this.gatewaySharedSecret = gatewaySharedSecret;
    }

    @Override
    public void sendNotification(Long userId, String type, String title, String body,
            String deeplink, Long actorUserId,
            String resourceType, String resourceId) {
        String url = notificationServiceProperties.getInternalUrl() + "/notification/internal/send";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-Secret", gatewaySharedSecret);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("type", type);
            request.put("title", title);
            request.put("body", body);
            request.put("deeplink", deeplink);
            request.put("actorUserId", actorUserId);
            request.put("resourceType", resourceType);
            request.put("resourceId", resourceId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            restTemplate.postForEntity(url, entity, Void.class);

            logger.debug("Notification sent successfully: userId={}, type={}", userId, type);
        } catch (Exception e) {
            // 알림 실패가 비즈니스 로직을 중단시키면 안 됨
            logger.warn("Failed to send notification: userId={}, type={}, error={}",
                    userId, type, e.getMessage());
        }
    }
}
