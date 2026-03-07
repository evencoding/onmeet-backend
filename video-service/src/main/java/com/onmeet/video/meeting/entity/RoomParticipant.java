package com.onmeet.video.meeting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "room_participants")
@EntityListeners(AuditingEntityListener.class)
public class RoomParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id")
    private MeetingRoom room;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantStatus status;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant leftAt;

    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeviceType deviceType;

    @Column(nullable = false)
    private boolean screenSharing;

    private Instant screenShareStartedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public RoomParticipant(MeetingRoom room, Long userId, ParticipantRole role,
                           ParticipantStatus status, Instant joinedAt, DeviceType deviceType) {
        this.room = room;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
        this.deviceType = deviceType;
    }

    public void leave(Instant now) {
        this.status = ParticipantStatus.LEFT;
        this.leftAt = now;
        this.durationSeconds = (int) Duration.between(this.joinedAt, now).getSeconds();
    }

    public void kick(Instant now) {
        this.status = ParticipantStatus.KICKED;
        this.leftAt = now;
        this.durationSeconds = (int) Duration.between(this.joinedAt, now).getSeconds();
    }

    public void disconnect(Instant now) {
        this.status = ParticipantStatus.DISCONNECTED;
        this.leftAt = now;
        this.durationSeconds = (int) Duration.between(this.joinedAt, now).getSeconds();
    }

    public void admit(Instant now) {
        this.status = ParticipantStatus.JOINED;
        this.joinedAt = now;
    }

    public void updateRole(ParticipantRole role) {
        this.role = role;
    }

    public boolean isActive() {
        return this.status == ParticipantStatus.JOINED || this.status == ParticipantStatus.WAITING;
    }

    public boolean isJoined() {
        return this.status == ParticipantStatus.JOINED;
    }

    public boolean isWaiting() {
        return this.status == ParticipantStatus.WAITING;
    }

    public void startScreenShare(Instant now) {
        this.screenSharing = true;
        this.screenShareStartedAt = now;
    }

    public void stopScreenShare() {
        this.screenSharing = false;
        this.screenShareStartedAt = null;
    }
}
