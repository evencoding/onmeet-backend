package com.onmeet.common.exception

/**
 * 비즈니스 로직에서 발생하는 예외의 기반 클래스.
 * ErrorCode를 통해 고유한 에러 코드, 한글 메시지, HTTP 상태를 운반한다.
 *
 * 사용 예:
 * ```
 * throw BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS)
 * ```
 */
open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
