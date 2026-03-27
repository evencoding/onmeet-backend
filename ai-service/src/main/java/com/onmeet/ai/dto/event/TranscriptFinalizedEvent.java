package com.onmeet.ai.dto.event;

import lombok.*;
import java.time.Instant;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class TranscriptFinalizedEvent {
    private Long roomId;
    private Long hostUserId;
    private String transcriptId;
    private String transcriptS3Key;
    private Long transcriptFileId;
    private int version;
    private Instant finalizedAt;
    private String meetingTitle;
    private List<MeetingEndedEvent.ParticipantInfo> participants;
}
