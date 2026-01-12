package com.onmeet.team.dto;

import java.time.Instant;

public record TeamResponse(
    Long id,
    String name,
    Instant createdAt,
    Instant updatedAt
) {
}
