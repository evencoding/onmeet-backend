package com.onmeet.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_stream")
public class NotificationStream {

    @Id
    @Column(name = "noti_stream_id")
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "last_event_id")
    private String lastEventId;

    @Builder
    public NotificationStream(String id, Long userId, String clientId, LocalDateTime connectedAt,
            LocalDateTime lastSeenAt, String lastEventId) {
        this.id = id;
        this.userId = userId;
        this.clientId = clientId;
        this.connectedAt = connectedAt;
        this.lastSeenAt = lastSeenAt;
        this.lastEventId = lastEventId;
    }
}
