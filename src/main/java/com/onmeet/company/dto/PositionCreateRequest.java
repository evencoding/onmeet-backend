package com.onmeet.company.dto;

import jakarta.validation.constraints.NotBlank;

public record PositionCreateRequest(
    @NotBlank String name
) {
}
