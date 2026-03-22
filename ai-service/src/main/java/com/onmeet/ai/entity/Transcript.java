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
@Table(name = "transcript")
public class Transcript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "transcript_id", length = 64, nullable = false, unique = true)
    private String transcriptId;

    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private TranscriptStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private Transcript(Long roomId, String transcriptId, int version, TranscriptStatus status) {
        this.roomId = roomId;
        this.transcriptId = transcriptId;
        this.version = version;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Transcript create(Long roomId, String transcriptId, int version) {
        return Transcript.builder()
                .roomId(roomId)
                .transcriptId(transcriptId)
                .version(version)
                .status(TranscriptStatus.COMPLETED)
                .build();
    }
}
