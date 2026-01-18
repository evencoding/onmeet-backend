package com.onmeet.voice.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.voice.dto.VoiceSegmentsSaveRequest;
import com.onmeet.voice.service.VoiceSegmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/meetings/{meetingId}/voice-segments")
public class VoiceSegmentInternalController {

    private final VoiceSegmentService voiceSegmentService;

    @PostMapping
    public ApiResponse<Void> save(
            @PathVariable String meetingId,
            @Valid @RequestBody VoiceSegmentsSaveRequest request
    ) {
        voiceSegmentService.save(meetingId, request);
        return ApiResponse.ok(null);
    }
}
