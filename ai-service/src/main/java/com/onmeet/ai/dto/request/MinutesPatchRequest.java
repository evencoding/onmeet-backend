package com.onmeet.ai.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MinutesPatchRequest {
    private String userEditedSummaryJson; // null 가능 (사용자 편집본)
}
