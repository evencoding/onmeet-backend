package com.onmeet.auth.dto;

import com.onmeet.company.entity.employee.EmployeeRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmployeeInviteSignupRequest(
    @NotBlank String token,
    @Email @NotBlank String email,
    @NotBlank String name,
    @NotBlank String employeeNo,
    @NotNull EmployeeRole role,
    @NotBlank @Size(min = 8, max = 72) String password,
    String departmentId,
    String positionId
) {
}
