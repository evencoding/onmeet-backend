package com.onmeet.voice.dto;

import java.time.Instant;

public record VoiceSegmentResponse(
        String id,
        String meetingId,
        String speakerUserId,
        long segmentsStartMs,
        long segmentsEndMs,
        String content,
        boolean isFinal,
        Instant createdAt
) {}
