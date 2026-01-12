package com.onmeet.meeting.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.meeting.dto.MeetingCreateRequest;
import com.onmeet.meeting.dto.MeetingResponse;
import com.onmeet.meeting.service.MeetingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping
    public ApiResponse<MeetingResponse> create(@Valid @RequestBody MeetingCreateRequest request) {
        return ApiResponse.ok(meetingService.create(request));
    }

    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingResponse> get(@PathVariable String meetingId) {
        return ApiResponse.ok(meetingService.get(meetingId));
    }

    @GetMapping
    public ApiResponse<List<MeetingResponse>> listByTeam(@RequestParam Long teamId) {
        return ApiResponse.ok(meetingService.listByTeam(teamId));
    }
}
