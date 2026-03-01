package com.onmeet.common.exception

/**
 * 모든 서비스 커스텀 예외의 기반 클래스.
 */
open class BaseException(
    override val message: String,
    val status: Int = 500
) : RuntimeException(message)

/**
 * 대상을 찾을 수 없을 때 발생하는 예외 (404 Not Found).
 */
open class EntityNotFoundException(message: String) : BaseException(message, 404)

/**
 * 권한이 부족하여 작업을 수행할 수 없을 때 발생하는 예외 (403 Forbidden).
 */
open class InsufficientPermissionException(message: String) : BaseException(message, 403)

/**
 * 타 회사 자산에 접근하려고 할 때 발생하는 예외 (403 Forbidden).
 */
open class CrossCompanyAccessException(message: String) : BaseException(message, 403)
