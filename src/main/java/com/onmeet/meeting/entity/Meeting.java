package com.onmeet.meeting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "meetings",
        indexes = {
                @Index(name = "idx_meetings_team", columnList = "team_id"),
                @Index(name = "idx_meetings_host", columnList = "host_user_id"),
                @Index(name = "idx_meetings_scheduled_at", columnList = "scheduled_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private UUID id;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "team_id", length = 36)
    private UUID teamId;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "host_user_id", length = 36, nullable = false)
    private UUID hostUserId;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "meet_tag", length = 100)
    private String meetTag;

    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @Column(name = "is_recording", nullable = false)
    private boolean recording = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 서비스에서 builder로만 생성
     */
    @Builder
    private Meeting(
            UUID teamId,
            UUID hostUserId,
            String title,
            String description,
            String meetTag,
            LocalDateTime scheduledAt
    ) {
        this.teamId = teamId;
        this.hostUserId = hostUserId;
        this.title = title;
        this.description = description;
        this.meetTag = meetTag;
        this.scheduledAt = scheduledAt;

        this.status = MeetingStatus.SCHEDULED;
        this.recording = false;
    }

    /* ===== 비즈니스 메서드 ===== */

    public void start() {
        this.status = MeetingStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    public void end() {
        this.status = MeetingStatus.ENDED;
        this.endedAt = LocalDateTime.now();
        this.recording = false;
    }

    public void cancel() {
        this.status = MeetingStatus.CANCELED;
    }

    public void changeHost(UUID newHostUserId) {
        this.hostUserId = newHostUserId;
    }

    public void setRecording(boolean on) {
        this.recording = on;
    }
}
