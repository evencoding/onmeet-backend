package com.onmeet.company.dto;

import com.onmeet.company.entity.department.DepartmentStatus;
import java.time.Instant;

public record DepartmentResponse(
    String id,
    String companyId,
    String name,
    DepartmentStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
