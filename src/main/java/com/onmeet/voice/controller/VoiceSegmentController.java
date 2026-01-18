package com.onmeet.voice.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.voice.dto.VoiceSegmentsResponse;
import com.onmeet.voice.service.VoiceSegmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/voice-segments")
public class VoiceSegmentController {

    private final VoiceSegmentService voiceSegmentService;

    @GetMapping
    public ApiResponse<VoiceSegmentsResponse> list(
            @PathVariable String meetingId,
            @RequestParam(required = false) Long fromMs,
            @RequestParam(required = false) Long toMs,
            @RequestParam(defaultValue = "500") int limit
    ) {
        return ApiResponse.ok(voiceSegmentService.list(meetingId, fromMs, toMs, limit));
    }
}