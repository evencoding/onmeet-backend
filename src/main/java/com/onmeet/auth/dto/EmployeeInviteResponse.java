package com.onmeet.auth.dto;

import com.onmeet.company.entity.employee.EmployeeInviteStatus;
import com.onmeet.company.entity.employee.EmployeeRole;
import java.time.Instant;

public record EmployeeInviteResponse(
    String id,
    String companyId,
    String email,
    EmployeeRole role,
    String employeeNo,
    String departmentId,
    String positionId,
    EmployeeInviteStatus status,
    String token,
    Instant createdAt,
    Instant expiresAt
) {
}
