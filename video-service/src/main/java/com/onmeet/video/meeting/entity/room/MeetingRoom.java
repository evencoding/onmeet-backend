package com.onmeet.video.meeting.entity.room;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "meeting_rooms")
@EntityListeners(AuditingEntityListener.class)
public class MeetingRoom {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String roomCode;

    @Column(nullable = false, unique = true, length = 100)
    private String livekitRoomName;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Long hostUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomType type;

    @Column(nullable = false)
    private int maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomAccessScope accessScope;

    private Long teamId;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(length = 255)
    private String password;

    private Instant scheduledAt;

    private Instant startedAt;

    private Instant endedAt;

    private Integer durationSeconds;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public MeetingRoom(String title, String description, Long hostUserId,
            RoomType type, int maxParticipants, String password, Instant scheduledAt,
            RoomAccessScope accessScope, Long teamId) {
        this.roomCode = generateRoomCode();
        this.livekitRoomName = "onmeet-" + UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.hostUserId = hostUserId;
        this.status = RoomStatus.WAITING;
        this.type = type;
        this.accessScope = accessScope != null ? accessScope : RoomAccessScope.ALL;
        this.teamId = teamId;
        this.maxParticipants = maxParticipants;
        this.locked = password != null && !password.isBlank();
        this.password = password;
        this.scheduledAt = scheduledAt;
    }

    public void updateInfo(String title, String description, Integer maxParticipants) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (maxParticipants != null && maxParticipants > 0) {
            this.maxParticipants = maxParticipants;
        }
    }

    public void start(Instant now) {
        this.status = RoomStatus.ACTIVE;
        this.startedAt = now;
    }

    public void end(Instant now) {
        this.status = RoomStatus.ENDED;
        this.endedAt = now;
        if (this.startedAt != null) {
            this.durationSeconds = (int) Duration.between(this.startedAt, now).getSeconds();
        }
    }

    public void cancel() {
        this.status = RoomStatus.CANCELLED;
    }

    public void lock(String password) {
        this.locked = true;
        this.password = password;
    }

    public void unlock() {
        this.locked = false;
        this.password = null;
    }

    public void regenerateCode() {
        this.roomCode = generateRoomCode();
    }

    public void updateSchedule(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public boolean isHost(Long userId) {
        return this.hostUserId.equals(userId);
    }

    public boolean isActive() {
        return this.status == RoomStatus.ACTIVE;
    }

    public boolean isWaiting() {
        return this.status == RoomStatus.WAITING;
    }

    public boolean isEnded() {
        return this.status == RoomStatus.ENDED || this.status == RoomStatus.CANCELLED;
    }

    private static String generateRoomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH + 1);
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
