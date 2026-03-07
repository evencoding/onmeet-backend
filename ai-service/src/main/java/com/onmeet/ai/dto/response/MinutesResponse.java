package com.onmeet.ai.dto.response;

import com.onmeet.ai.entity.Minutes;
import com.onmeet.ai.enums.MinutesStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class MinutesResponse {
    private Long id;
    private Long roomId;
    private String transcriptId;
    private String transcriptS3Key;
    private String summaryS3Key;

    private String summaryJson;
    private String userEditedSummaryJson;

    private MinutesStatus status;


    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    public static MinutesResponse from(Minutes m) {
        return MinutesResponse.builder()
                .id(m.getId())
                .roomId(m.getRoomId())
                .transcriptId(m.getTranscriptId())
                .transcriptS3Key(m.getTranscriptS3Key())
                .summaryS3Key(m.getSummaryS3Key())
                .summaryJson(m.getSummaryJson())
                .userEditedSummaryJson(m.getUserEditedSummaryJson())
                .status(m.getStatus())
                .lastError(m.getLastError())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
