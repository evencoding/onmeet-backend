package com.onmeet.meeting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "meetings")
@Getter @Setter @NoArgsConstructor
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // 자동으로 UUID 생성
    @Column(columnDefinition = "char(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "team_id", columnDefinition = "char(36)", nullable = true)
    private String teamId;
//    host_user_id 나중에 not null 으로
    @Column(name = "host_user_id", columnDefinition = "char(36)", nullable = true)
    private String hostUserId;

    @Column(length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "meet_tag", length = 100)
    private String meetTag;

    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @Column(name = "is_recording", columnDefinition = "TINYINT(1)")
    private boolean isRecording = false;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('SCHEDULED','IN_PROGRESS','ENDED','CANCELED')")
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum MeetingStatus { SCHEDULED, IN_PROGRESS, ENDED, CANCELED }
}