package com.onmeet.notification.entity;

import com.onmeet.notification.type.NotificationStatus;
import com.onmeet.notification.type.NotificationType;
import com.onmeet.notification.type.ResourceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "noti_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private String title;

    private String body;

    private String deeplink;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "dedupe_key", unique = true)
    private String dedupeKey;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotificationRecipient> recipients = new ArrayList<>();

    @Builder
    public Notification(NotificationType type, NotificationStatus status, String title, String body, String deeplink,
            LocalDateTime scheduledAt, ResourceType resourceType, String dedupeKey,
            String resourceId, Long actorUserId) {
        this.type = type;
        this.status = status != null ? status : NotificationStatus.PENDING;
        this.title = title;
        this.body = body;
        this.deeplink = deeplink;
        this.scheduledAt = scheduledAt;
        this.resourceType = resourceType;
        this.dedupeKey = dedupeKey;
        this.resourceId = resourceId;
        this.actorUserId = actorUserId;
    }

    public void addRecipient(NotificationRecipient recipient) {
        this.recipients.add(recipient);
        recipient.setNotification(this);
    }

    public void markAsSent() {
        this.status = NotificationStatus.SENT;
    }

    public void markAsFailed() {
        this.status = NotificationStatus.FAILED;
    }
}
