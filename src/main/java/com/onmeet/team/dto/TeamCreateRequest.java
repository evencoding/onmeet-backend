package com.onmeet.team.dto;

import jakarta.validation.constraints.NotBlank;

public record TeamCreateRequest(
    @NotBlank String companyId,
    @NotBlank String name
) {
}
