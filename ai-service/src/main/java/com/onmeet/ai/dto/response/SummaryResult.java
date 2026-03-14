package com.onmeet.ai.dto.response;

import lombok.*;
import java.util.List;

/**
 * Claude 요약본 결과를 담는 구조화된 DTO.
 * 단순 문자열이 아닌 섹션별(요약, 중요 키워드, 액션 아이템 등)로 나누어 클라이언트에 제공.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SummaryResult {

    private String description;
    private List<String> keywords;
    private List<String> decisions;
    private List<String> actionItems;

}
