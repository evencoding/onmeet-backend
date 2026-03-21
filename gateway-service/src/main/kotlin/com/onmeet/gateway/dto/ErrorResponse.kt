package com.onmeet.gateway.dto

import java.time.Instant

data class ErrorDetail(
    val code: String,
    val status: Int,
    val message: String,
    val timestamp: Long = Instant.now().toEpochMilli()
)

data class ErrorResponse(
    val success: Boolean = false,
    val error: ErrorDetail
)
