package com.onmeet.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "fcm_token", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "device_id" })
})
public class FcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "device_type")
    private String deviceType; // "WEB", "ANDROID", "IOS"

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public FcmToken(Long userId, String token, String deviceId, String deviceType) {
        this.userId = userId;
        this.token = token;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
    }

    public void updateToken(String newToken) {
        this.token = newToken;
    }
}
