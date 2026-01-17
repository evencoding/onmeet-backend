package com.onmeet.meeting.controller;

import com.onmeet.meeting.dto.MeetingCreateRequest;
import com.onmeet.meeting.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {
    private final MeetingService meetingService;

    // 회의방 생성
    @PostMapping
    public ResponseEntity<String> create(@RequestBody MeetingCreateRequest request) {
        String roomId = meetingService.createMeeting(request);
        return ResponseEntity.ok(roomId);
    }
}
