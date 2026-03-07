package com.onmeet.ai.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MinutesRegenerateRequest {
    private String language; // 예: "ko"
    private String style; // 예: "default"
    private String model; // 예: "claude-sonnet"
}
