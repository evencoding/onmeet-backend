package com.onmeet.common.exception

/**
 * 기본 시스템 예외. 모든 비즈니스 예외는 이 클래스를 상속받아야 합니다.
 */
open class BaseException(
    override val message: String,
    val status: Int = 500
) : RuntimeException(message)

/**
 * 엔티티를 찾을 수 없을 때 발생하는 예외.
 */
open class EntityNotFoundException(message: String) : BaseException(message, 404)

/**
 * 권한이 부족할 때 발생하는 예외.
 */
open class InsufficientPermissionException(message: String) : BaseException(message, 403)
