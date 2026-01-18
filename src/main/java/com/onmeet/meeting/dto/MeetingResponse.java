package com.onmeet.meeting.dto;

import com.onmeet.meeting.entity.MeetingStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MeetingResponse(
        UUID id,
        UUID teamId,
        UUID hostUserId,
        String title,
        String description,
        String meetTag,
        LocalDateTime scheduledAt,
        MeetingStatus status,
        boolean recording,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
