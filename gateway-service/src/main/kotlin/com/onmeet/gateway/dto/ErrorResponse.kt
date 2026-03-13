package com.onmeet.gateway.dto

import java.time.Instant

data class ErrorResponse(
    val code: String,
    val status: Int,
    val message: String,
    val timestamp: Long = Instant.now().toEpochMilli()
)
