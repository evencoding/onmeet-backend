package com.onmeet.ai.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.entity.Minutes;
import com.onmeet.ai.enums.MinutesStatus;
import com.onmeet.common.dto.ai.SummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "회의록 단건 응답 정보를 담는 DTO")
public class MinutesResponse {
    
    @Schema(description = "회의록 ID", example = "1")
    private Long id;

    @Schema(description = "회의실 ID", example = "100")
    private Long roomId;

    @Schema(description = "원본 오디오와 연결된 트랜스크립트 ID", example = "transcript-12345")
    private String transcriptId;

    @Schema(description = "트랜스크립트 JSON 파일 S3 경로", example = "s3/transcripts/100/transcript-12345.json")
    private String transcriptS3Key;

    @Schema(description = "요약본 JSON 파일 S3 경로", example = "s3/summaries/100/transcript-12345_summary.json")
    private String summaryS3Key;

    @Schema(description = "분리된 회의록 요약 데이터")
    private SummaryResult summary;

    @Schema(description = "통짜 AI 생성 요약 원본 문자열(JSON)", example = "{\"description\":\"...\"}")
    private String summaryJson;

    @Schema(description = "사용자가 편집한 요약 문자열(JSON)", example = "null")
    private String userEditedSummaryJson;

    @Schema(description = "회의록 생성 상태", example = "GENERATED")
    private MinutesStatus status;

    @Schema(description = "마지막 오류 메시지 (있는 경우)", example = "null")
    private String lastError;

    @Schema(description = "최초 생성 시간")
    private Instant createdAt;

    @Schema(description = "마지막 수정 시간")
    private Instant updatedAt;

    private static final ObjectMapper OM = new ObjectMapper();

    public static MinutesResponse from(Minutes m) {
        SummaryResult parsedSummary = null;
        if (m.getSummaryJson() != null) {
            try {
                parsedSummary = OM.readValue(m.getSummaryJson(), SummaryResult.class);
            } catch (Exception ignored) {
                // 파싱 실패 시 null로 반환
            }
        }

        return MinutesResponse.builder()
                .id(m.getId())
                .roomId(m.getRoomId())
                .transcriptId(m.getTranscriptId())
                .transcriptS3Key(m.getTranscriptS3Key())
                .summaryS3Key(m.getSummaryS3Key())
                .summary(parsedSummary)
                .summaryJson(m.getSummaryJson())
                .userEditedSummaryJson(m.getUserEditedSummaryJson())
                .status(m.getStatus())
                .lastError(m.getLastError())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
