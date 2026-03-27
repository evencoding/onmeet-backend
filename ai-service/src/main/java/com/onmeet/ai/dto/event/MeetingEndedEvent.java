package com.onmeet.ai.dto.event;

import lombok.*;
import java.time.Instant;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class MeetingEndedEvent {
    private Long roomId;
    private Long hostUserId;
    private Instant startedAt;
    private Instant endedAt;
    private String title;
    private String description;
    private List<ParticipantInfo> participants;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class ParticipantInfo {
        private Long userId;
        private String name;
        private String role;
    }
}
