package com.onmeet.minutes.dto;

public record MinutesGenerateRequest (
        // 추후 확장 가능 요소 추가 (회의록 생성 요청 시, 언어 선정, 요약 방식 등)
        String language,
        String style
) {
}
