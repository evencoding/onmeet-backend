package com.onmeet.video.meeting.event;

import com.onmeet.common.dto.NotificationRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishNotification(NotificationRequestDto request) {
        try {
            kafkaTemplate.send("notification.send", request);
            log.info("Published notification event: type={}, userId={}, userIds={}",
                    request.getType(), request.getUserId(), request.getUserIds());
        } catch (Exception e) {
            log.error("Failed to publish notification event: type={}, error={}",
                    request.getType(), e.getMessage());
        }
    }
}
