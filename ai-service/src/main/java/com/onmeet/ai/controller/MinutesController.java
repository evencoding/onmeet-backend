package com.onmeet.ai.controller;

import com.onmeet.ai.dto.request.MinutesPatchRequest;
import com.onmeet.ai.dto.request.MinutesRegenerateRequest;
import com.onmeet.ai.dto.response.MinutesResponse;
import com.onmeet.ai.service.MinutesService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/minutes")
public class MinutesController {

    private final MinutesService minutesService;

    public MinutesController(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    @GetMapping("/{meetingId}")
    public MinutesResponse get(@PathVariable String meetingId) {
        return minutesService.get(meetingId);
    }

    @PostMapping("/{meetingId}/regenerate")
    public MinutesResponse regenerate(
            @PathVariable String meetingId,
            @RequestBody(required = false) MinutesRegenerateRequest req
    ) {
        return minutesService.regenerate(meetingId, req);
    }

    @PatchMapping("/{meetingId}")
    public MinutesResponse patch(
            @PathVariable String meetingId,
            @RequestBody MinutesPatchRequest req
    ) {
        return minutesService.patch(meetingId, req);
    }

    // 테스트 편의용: transcript json 확인
    @GetMapping(value = "/{meetingId}/transcript", produces = MediaType.APPLICATION_JSON_VALUE)
    public String transcript(@PathVariable String meetingId) {
        return minutesService.getTranscriptRawJson(meetingId);
    }
}
