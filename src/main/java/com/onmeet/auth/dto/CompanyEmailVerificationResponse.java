package com.onmeet.auth.dto;

import com.onmeet.company.entity.company.CompanyEmailVerificationStatus;
import com.onmeet.company.entity.company.CompanySize;
import java.time.Instant;

public record CompanyEmailVerificationResponse(
    String id,
    String email,
    String companyName,
    String domain,
    CompanySize companySize,
    CompanyEmailVerificationStatus status,
    String token,
    Instant createdAt,
    Instant verifiedAt,
    Instant expiresAt
) {
}
