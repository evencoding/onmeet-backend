package com.onmeet.voice.dto;

import java.util.List;

public record VoiceSegmentsResponse(
        String meetingId,
        List<VoiceSegmentResponse> items
) {}