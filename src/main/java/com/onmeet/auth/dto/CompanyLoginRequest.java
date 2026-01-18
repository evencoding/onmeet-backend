package com.onmeet.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CompanyLoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password,
    String companyId,
    String companyDomain
) {
}
