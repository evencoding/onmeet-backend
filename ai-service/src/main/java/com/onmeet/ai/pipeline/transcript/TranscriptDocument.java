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

        @com.fasterxml.jackson.annotation.JsonAlias({"actorId", "participantId"})
        private String participantId;

        @com.fasterxml.jackson.annotation.JsonAlias({"actorName", "participantName", "senderIdentity", "participantIdentity"})
        private String participantName;

        private Instant timestamp;
        private Long seq;
        private String text;

        // optional(voice)
        private Long segmentStartMs;
        private Long segmentEndMs;
    }
}
