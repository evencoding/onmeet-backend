package com.onmeet.minutes.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.minutes.dto.MinutesCreateRequest;
import com.onmeet.minutes.dto.MinutesResponse;
import com.onmeet.minutes.service.MinutesService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/minutes")
public class MinutesController {

    private final MinutesService minutesService;

    public MinutesController(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    @PostMapping
    public ApiResponse<MinutesResponse> create(@Valid @RequestBody MinutesCreateRequest request) {
        return ApiResponse.ok(minutesService.create(request));
    }

    @GetMapping("/meeting/{meetingId}")
    public ApiResponse<MinutesResponse> getByMeeting(@PathVariable String meetingId) {
        return ApiResponse.ok(minutesService.getByMeeting(meetingId));
    }
}
