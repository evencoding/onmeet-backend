package com.onmeet.video.meeting.dto.room;

public record MonthlyStatsResponse(
    int year,
    int month,
    long totalMeetings,
    long totalDurationSeconds,
    long totalParticipants
) {
}
