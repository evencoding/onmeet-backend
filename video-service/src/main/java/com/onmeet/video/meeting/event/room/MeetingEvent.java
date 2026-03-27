package com.onmeet.video.meeting.event.room;

import java.time.Instant;
import java.util.List;

public record MeetingEvent(
    String type,
    Long roomId,
    Long hostUserId,
    int participantCount,
    Instant startedAt,
    Instant endedAt,
    String title,
    String description,
    List<ParticipantInfo> participants
) {

    public MeetingEvent(String type, Long roomId, Long hostUserId, int participantCount,
                        Instant startedAt, Instant endedAt) {
        this(type, roomId, hostUserId, participantCount, startedAt, endedAt, null, null, null);
    }

    public record ParticipantInfo(
        Long userId,
        String name,
        String role
    ) {}
}
