package com.onmeet.ai.pipeline.transcript;

import lombok.*;

import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class TranscriptDocument {

    private String meetingId;
    private String transcriptId;
    private int version;

    private List<Event> events;

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Event {
        private String id;      // messageId or segmentId
        private String type;    // CHAT | VOICE
        private String actorId; // senderId or participantId

        private Long atMs;      // CHAT: atMs / VOICE: startMs
        private Long seq;       // tie-breaker
        private String text;

        // optional(voice)
        private Long startMs;
        private Long endMs;
    }
}
