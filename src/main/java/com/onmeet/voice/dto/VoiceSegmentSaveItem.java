package com.onmeet.voice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VoiceSegmentSaveItem(
        String speakerUserId,
        @NotNull Long segmentsStartMs,
        @NotNull Long segmentsEndMs,
        @NotBlank String content
) {}