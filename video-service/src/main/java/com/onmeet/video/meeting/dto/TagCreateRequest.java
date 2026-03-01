package com.onmeet.video.meeting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagCreateRequest(
    @NotBlank @Size(max = 50) String tagName
) {
}
