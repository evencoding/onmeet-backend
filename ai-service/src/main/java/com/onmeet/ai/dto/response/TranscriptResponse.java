package com.onmeet.ai.dto.response;

import java.time.LocalDateTime;

public record TranscriptResponse(
        Long roomId,
        String transcript,
        LocalDateTime createdAt
) {}
