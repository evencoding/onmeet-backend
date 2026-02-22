package com.onmeet.meeting.dto;

public record MonthlyStatsResponse(
    int year,
    int month,
    long totalMeetings,
    long totalDurationSeconds,
    long totalParticipants
) {
}
