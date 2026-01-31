package com.onmeet.chat.exception;

public record ChatErrorResponse(
        String code,
        String message
) {}
