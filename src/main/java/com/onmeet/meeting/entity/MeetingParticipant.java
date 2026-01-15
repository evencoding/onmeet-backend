package com.onmeet.meeting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_participants")
@Getter @Setter @NoArgsConstructor
public class MeetingParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // 자동으로 UUID 생성
    @Column(columnDefinition = "char(36)", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", columnDefinition = "char(36)", nullable = false)
    private Meeting meeting;

//    테스트용 나중에 not null 로 수정하기
    @Column(name = "user_id", columnDefinition = "char(36)", nullable = true)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('HOST','PARTICIPANT')")
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_status", columnDefinition = "ENUM('INVITED','JOINED','ABSENT')")
    private JoinStatus joinStatus = JoinStatus.INVITED;

    @Column(name = "mic_on", columnDefinition = "TINYINT(1)")
    private boolean micOn = false;

    @Column(name = "cam_on", columnDefinition = "TINYINT(1)")
    private boolean camOn = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum UserRole { HOST, PARTICIPANT }
    public enum JoinStatus { INVITED, JOINED, ABSENT }
}