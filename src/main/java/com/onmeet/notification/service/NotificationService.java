package com.onmeet.notification.service;

import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
import com.onmeet.common.util.ClockProvider;
import com.onmeet.notification.dto.NotificationCreateRequest;
import com.onmeet.notification.dto.NotificationResponse;
import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.repository.NotificationRecipientRepository;
import com.onmeet.notification.repository.NotificationRepository;
import com.onmeet.notification.sse.NotificationPublisher;
import com.onmeet.user.entity.User;
import com.onmeet.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final NotificationPublisher notificationPublisher;
    private final ClockProvider clockProvider;

    public NotificationService(
        NotificationRepository notificationRepository,
        NotificationRecipientRepository recipientRepository,
        UserRepository userRepository,
        NotificationPublisher notificationPublisher,
        ClockProvider clockProvider
    ) {
        this.notificationRepository = notificationRepository;
        this.recipientRepository = recipientRepository;
        this.userRepository = userRepository;
        this.notificationPublisher = notificationPublisher;
        this.clockProvider = clockProvider;
    }

    @Transactional
    public NotificationResponse create(NotificationCreateRequest request) {
        Notification notification = new Notification(
            request.type(),
            request.resourceType(),
            request.resourceId(),
            request.title(),
            request.body()
        );
        Notification saved = notificationRepository.save(notification);

        List<User> users = userRepository.findAllById(request.recipientUserIds());
        if (users.size() != request.recipientUserIds().size()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Invalid recipient user id");
        }

        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, user -> user));
        Instant deliveredAt = clockProvider.now();
        List<NotificationRecipient> recipients = request.recipientUserIds().stream()
            .map(userId -> {
                NotificationRecipient recipient = new NotificationRecipient(saved, userMap.get(userId));
                recipient.markDelivered(deliveredAt);
                return recipient;
            })
            .collect(Collectors.toList());
        recipientRepository.saveAll(recipients);

        NotificationResponse response = toResponse(saved);
        request.recipientUserIds().forEach(userId -> notificationPublisher.publish(userId, response));
        return response;
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getResourceType(),
            notification.getResourceId(),
            notification.getTitle(),
            notification.getBody(),
            notification.getCreatedAt()
        );
    }
}
