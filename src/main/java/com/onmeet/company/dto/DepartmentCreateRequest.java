package com.onmeet.company.dto;

import jakarta.validation.constraints.NotBlank;

public record DepartmentCreateRequest(
    @NotBlank String name
) {
}
