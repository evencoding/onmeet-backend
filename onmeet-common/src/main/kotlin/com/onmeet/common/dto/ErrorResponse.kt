package com.onmeet.common.dto

import java.time.Instant

/**
 * 모든 마이크로서비스에서 공통으로 사용할 에러 응답 구조.
 */
data class ErrorResponse @JvmOverloads constructor(
    val status: Int,
    val message: String,
    val timestamp: Long = Instant.now().toEpochMilli()
)
