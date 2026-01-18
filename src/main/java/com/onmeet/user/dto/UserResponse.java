package com.onmeet.user.dto;

import java.time.Instant;

public record UserResponse(
    String id,
    String email,
    String name,
    Instant createdAt,
    Instant updatedAt
) {
}
