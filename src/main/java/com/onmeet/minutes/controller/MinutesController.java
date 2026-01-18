package com.onmeet.minutes.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.minutes.dto.*;
import com.onmeet.minutes.service.MinutesService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/minutes")
public class MinutesController {

    private final MinutesService minutesService;

    public MinutesController(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    @GetMapping
    public MinutesResponse getMinutesByMeeting(@PathVariable String meetingId) {
        return minutesService.getMinutesByMeeting(meetingId);
    }

    @GetMapping("/status")
    public MinutesJobStatusResponse getJobStatusByMeetingId(@PathVariable String meetingId) {
        return minutesService.getJobStatusByMeetingId(meetingId);
    }

    @PostMapping("/generate")
    public MinutesGenerateResponse generate(
            @PathVariable String meetingId,
            @RequestBody(required = false) MinutesGenerateRequest request
    ) {
        if (request == null) request = new MinutesGenerateRequest(null, null);
        return minutesService.generate(meetingId, request);
    }

    @PostMapping("/retry")
    public MinutesGenerateResponse retry(@PathVariable String meetingId) {
        return minutesService.retry(meetingId);
    }
}
