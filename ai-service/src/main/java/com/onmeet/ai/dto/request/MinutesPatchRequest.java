package com.onmeet.ai.dto.request;

import com.onmeet.ai.enums.MinutesAccessScope;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MinutesPatchRequest {
    private MinutesAccessScope accessScope; // null 가능
    private String userEditedSummaryJson; // null 가능 (사용자 편집본)
}
