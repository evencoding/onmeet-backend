package com.onmeet.ai.dto.event;

import lombok.*;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecordingStoredEvent {
    private Long fileId;
    private Long roomId;
    private Long recordingId;
    private String s3Path;
    private String s3Url;
    private Long fileSizeBytes;
    private Integer durationSeconds;
    private String participantIdentity;
    private String timestamp;
}
