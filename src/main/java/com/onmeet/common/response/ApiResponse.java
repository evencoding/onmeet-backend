package com.onmeet.common.response;

import com.onmeet.common.exception.ApiErrorResponse;
import java.time.Instant;

public record ApiResponse<T>(
    boolean success,
    T data,
    ApiErrorResponse error,
    Instant timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static ApiResponse<Void> error(ApiErrorResponse error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
