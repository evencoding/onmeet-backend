package com.onmeet.notification.entity;

import com.onmeet.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_recipients", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(columnNames = { "notification_id", "user_id" })
})
@EntityListeners(AuditingEntityListener.class)
public class NotificationRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "notification_id")
    private Notification notification;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Instant deliveredAt;

    private Instant readAt;

    public NotificationRecipient(Notification notification, User user) {
        this.notification = notification;
        this.user = user;
    }

    public void markDelivered(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
