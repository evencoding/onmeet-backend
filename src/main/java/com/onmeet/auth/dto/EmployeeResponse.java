package com.onmeet.auth.dto;

import com.onmeet.company.entity.employee.EmployeeRole;
import com.onmeet.company.entity.employee.EmployeeStatus;
import java.time.Instant;

public record EmployeeResponse(
    String id,
    String userId,
    String companyId,
    EmployeeRole role,
    EmployeeStatus status,
    String employeeNo,
    String departmentId,
    String positionId,
    Instant createdAt
) {
}
