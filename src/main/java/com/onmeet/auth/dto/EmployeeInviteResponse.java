package com.onmeet.auth.dto;

import com.onmeet.company.entity.EmployeeInviteStatus;
import com.onmeet.company.entity.EmployeeRole;
import java.time.Instant;

public record EmployeeInviteResponse(
    String id,
    String companyId,
    String email,
    EmployeeRole role,
    String employeeNo,
    EmployeeInviteStatus status,
    String token,
    Instant createdAt,
    Instant expiresAt
) {
}
