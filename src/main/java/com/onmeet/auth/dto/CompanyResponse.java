package com.onmeet.auth.dto;

import com.onmeet.company.entity.company.CompanySize;
import com.onmeet.company.entity.company.CompanyStatus;
import java.time.Instant;

public record CompanyResponse(
    String id,
    String name,
    String domain,
    CompanySize companySize,
    CompanyStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
