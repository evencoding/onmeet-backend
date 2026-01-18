package com.onmeet.meeting.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.meeting.dto.MeetingCreateRequest;
import com.onmeet.meeting.dto.MeetingResponse;
import com.onmeet.meeting.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
@Slf4j
public class MeetingController {

    private final MeetingService meetingService;

    // 회의 생성 (임시: X-USER-ID 헤더로 hostUserId 받기)
    @PostMapping
    public ApiResponse<UUID> create(
            @RequestHeader("X-USER-ID") UUID userId,
            @RequestBody @Valid MeetingCreateRequest req
    ) {
        log.info("POST /api/v1/meetings called, userId={}, teamId={}", userId, req.teamId()); // ✅ 여기(첫줄)

        UUID meetingId = meetingService.createMeeting(userId, req);
        return ApiResponse.ok(meetingId);
    }

    // 오늘 회의 목록
    @GetMapping("/today")
    public ResponseEntity<List<MeetingResponse>> getTodayMeetings() {
        return ResponseEntity.ok(meetingService.getTodayMeetings());
    }

    // 이전 회의 목록 (커서 기반)
    @GetMapping("/past")
    public ResponseEntity<List<MeetingResponse>> getPastMeetings(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime cursor,

            @RequestParam(defaultValue = "10")
            int size
    ) {
        return ResponseEntity.ok(meetingService.getPastMeetings(cursor, size));
    }
}
