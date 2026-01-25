package com.onmeet.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanySignupRequest(
    @Email @NotBlank String representativeEmail,
    @NotBlank String representativeName,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank String companyName,
    @NotBlank String domain,
    @NotBlank String verificationToken,
    String employeeNo
) {
}
