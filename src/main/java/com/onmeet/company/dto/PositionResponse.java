package com.onmeet.company.dto;

import com.onmeet.company.entity.position.PositionStatus;
import java.time.Instant;

public record PositionResponse(
    String id,
    String companyId,
    String name,
    PositionStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
