package com.onmeet.auth.dto;

import com.onmeet.company.entity.CompanyStatus;
import java.time.Instant;

public record CompanyResponse(
    String id,
    String name,
    String domain,
    CompanyStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
