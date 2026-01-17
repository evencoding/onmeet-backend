package com.onmeet.auth.dto;

import com.onmeet.company.entity.EmployeeRole;
import com.onmeet.company.entity.EmployeeStatus;
import java.time.Instant;

public record EmployeeResponse(
    String id,
    String userId,
    String companyId,
    EmployeeRole role,
    EmployeeStatus status,
    String employeeNo,
    Instant createdAt
) {
}
