package com.onmeet.ai.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "회의록 재생성 요청 DTO")
public class MinutesRegenerateRequest {
    
    @Schema(description = "요약 언어", example = "ko")
    private String language; 

    @Schema(description = "요약 스타일", example = "default")
    private String style; 

    @Schema(description = "사용할 AI 모델 명", example = "claude-sonnet")
    private String model; 
}
