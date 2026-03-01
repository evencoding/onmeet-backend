package com.onmeet.video.meeting.dto;

public record MonthlyStatsResponse(
    int year,
    int month,
    long totalMeetings,
    long totalDurationSeconds,
    long totalParticipants
) {
}
