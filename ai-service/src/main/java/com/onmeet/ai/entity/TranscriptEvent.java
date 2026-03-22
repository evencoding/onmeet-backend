package com.onmeet.ai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "transcript_event", indexes = {
        @Index(name = "idx_transcript_event_transcript_id", columnList = "transcript_id")
})
public class TranscriptEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transcript_id", length = 64, nullable = false)
    private String transcriptId;

    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "type", length = 8, nullable = false)
    private String type;  // VOICE | CHAT

    /** 회원 userId (회원인 경우에만 존재, 비회원은 null) */
    @Column(name = "participant_id", length = 128, nullable = true)
    private String participantId;

    /** 발화자/발신자 표시 이름 (회원/비회원 모두 사용자가 설정한 이름) */
    @Column(name = "participant_name", length = 128, nullable = false)
    private String participantName;

    @Column(name = "seq", nullable = false)
    private Long seq;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Lob
    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(name = "segment_start_ms")
    private Long segmentStartMs;  // VOICE 전용

    @Column(name = "segment_end_ms")
    private Long segmentEndMs;    // VOICE 전용

    @Builder
    private TranscriptEvent(String transcriptId, String eventId, String type,
                            String participantId, String participantName,
                            Long seq, Instant timestamp, String text,
                            Long segmentStartMs, Long segmentEndMs) {
        this.transcriptId = transcriptId;
        this.eventId = eventId;
        this.type = type;
        this.participantId = participantId;
        this.participantName = participantName;
        this.seq = seq;
        this.timestamp = timestamp;
        this.text = text;
        this.segmentStartMs = segmentStartMs;
        this.segmentEndMs = segmentEndMs;
    }
}
