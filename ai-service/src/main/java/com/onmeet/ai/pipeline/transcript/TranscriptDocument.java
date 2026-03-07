package com.onmeet.ai.pipeline.transcript;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class TranscriptDocument {

    private Long roomId;
    private String transcriptId;
    private int version;

    private List<Event> events;

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Event {
        private String id;      // messageId or segmentId
        private String type;    // CHAT | VOICE
        private String actorId; // senderId or userId (String for JSON flexibility)

        private Instant timestamp; // CHAT: timestamp / VOICE: timestamp (or startMs as Instant)
        private Long seq;          // tie-breaker
        private String text;

        // optional(voice)
        private Long startMs;
        private Long endMs;
    }
}
