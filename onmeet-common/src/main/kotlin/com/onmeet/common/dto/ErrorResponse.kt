package com.onmeet.common.dto

import java.time.Instant

/**
 * 모든 마이크로서비스에서 공통으로 사용할 에러 응답 구조.
 * 
 * @property status HTTP 상태 코드 (예: 400, 404, 500)
 * @property message 사용자 또는 개발자에게 전달할 에러 메시지
 * @property timestamp 에러 발생 시각 (Unix Timestamp, Milliseconds)
 */
data class ErrorResponse @JvmOverloads constructor(
    val status: Int,
    val message: String,
    val timestamp: Long = Instant.now().toEpochMilli()
)
