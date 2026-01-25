package com.onmeet.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record MeetingCreateRequest(
        @NotBlank String title,
        String description,
        String meetTag,
        UUID teamId,

        List<String> invitedEmails,

        @NotNull LocalDate date,
        @NotNull LocalTime time
) {}
