package com.onmeet.voice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record VoiceSegmentsSaveRequest(
        @NotEmpty @Valid List<VoiceSegmentSaveItem> items
) {}