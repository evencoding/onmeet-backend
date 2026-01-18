package com.onmeet.meeting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "meeting_participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_meeting_participant",
                        columnNames = {"meeting_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_participants_meeting", columnList = "meeting_id"),
                @Index(name = "idx_participants_user", columnList = "user_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "user_id", columnDefinition = "char(36)", nullable = false, length = 36)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_status", nullable = false, length = 20)
    private JoinStatus joinStatus = JoinStatus.INVITED;

    @Column(name = "mic_on", nullable = false)
    private boolean micOn = false;

    @Column(name = "cam_on", nullable = false)
    private boolean camOn = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public static MeetingParticipant create(Meeting meeting, UUID userId) {
        MeetingParticipant p = new MeetingParticipant();
        p.meeting = meeting;
        p.userId = userId;
        p.joinStatus = JoinStatus.INVITED;
        return p;
    }

    public void join() {
        this.joinStatus = JoinStatus.JOINED;
    }

    public void markAbsent() {
        this.joinStatus = JoinStatus.ABSENT;
    }

    public void toggleMic(boolean on) {
        this.micOn = on;
    }

    public void toggleCam(boolean on) {
        this.camOn = on;
    }
}
