package com.onmeet.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanyEmailVerificationConfirmRequest(
    @NotBlank String token
) {
}
