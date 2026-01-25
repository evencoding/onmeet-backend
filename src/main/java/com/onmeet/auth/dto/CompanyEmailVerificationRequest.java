package com.onmeet.auth.dto;

import com.onmeet.company.entity.company.CompanySize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompanyEmailVerificationRequest(
    @Email @NotBlank String representativeEmail,
    @NotBlank String companyName,
    @NotBlank String domain,
    @NotNull CompanySize companySize
) {
}
