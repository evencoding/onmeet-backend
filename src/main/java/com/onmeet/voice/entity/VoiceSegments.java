package com.onmeet.voice.entity;

import com.onmeet.meeting.entity.Meeting;
import com.onmeet.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "voice_segments",
        indexes = {
                @Index(name = "idx_vs_meeting_time", columnList = "meeting_id, segments_start_ms"),
                @Index(name = "idx_vs_meeting_speaker", columnList = "meeting_id, speaker_user_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class VoiceSegments {

    @Id
    @UuidGenerator
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    // v1: 내부 사용자 발화 기준 (게스트 발화 지원하면 guest FK 추가)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "speaker_user_id")
    private User speakerUser;

    @Column(name = "segments_start_ms", nullable = false)
    private long segmentsStartMs;

    @Column(name = "segments_end_ms", nullable = false)
    private long segmentsEndMs;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    // v1: final만 저장하면 항상 true지만, 운영/확장 대비로 유지
    @Column(name = "is_final", nullable = false)
    private boolean isFinal = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public VoiceSegments(Meeting meeting, User speakerUser, long startMs, long endMs, String content) {
        this.meeting = meeting;
        this.speakerUser = speakerUser;
        this.segmentsStartMs = startMs;
        this.segmentsEndMs = endMs;
        this.content = content;
        this.isFinal = true;
    }
}
