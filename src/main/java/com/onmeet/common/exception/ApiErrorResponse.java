package com.onmeet.common.exception;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
    String code,
    String message,
    Instant timestamp,
    List<FieldError> errors
) {

    public record FieldError(String field, String reason) {
    }
}
