package com.onmeet.auth.dto;

import com.onmeet.company.entity.EmployeeRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmployeeInviteCreateRequest(
    @NotBlank String companyId,
    @NotBlank String inviterUserId,
    @Email @NotBlank String email,
    @NotNull EmployeeRole role,
    String employeeNo
) {
}
