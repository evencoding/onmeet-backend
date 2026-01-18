package com.onmeet.meeting.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record MeetingUpdateRequest(
        String title,
        String description,
        String meetTag,

        UUID teamId,

        LocalDate date,
        LocalTime time
) {}
