package com.onmeet.team.dto;

import java.time.Instant;

public record TeamResponse(
    String id,
    String companyId,
    String name,
    Instant createdAt,
    Instant updatedAt
) {
}
