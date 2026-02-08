package com.onmeet.common.exception

import com.onmeet.common.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * 모든 마이크로서비스에서 상속받아 사용할 기본 전역 예외 핸들러.
 */
abstract class BaseGlobalExceptionHandler {
    protected val logger = LoggerFactory.getLogger(this.javaClass)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.error("Illegal Argument: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.message ?: "Invalid argument"))
    }

    @ExceptionHandler(InsufficientPermissionException::class)
    fun handleInsufficientPermissionException(e: InsufficientPermissionException): ResponseEntity<ErrorResponse> {
        logger.error("Insufficient permission: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(HttpStatus.FORBIDDEN.value(), e.message))
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFoundException(e: EntityNotFoundException): ResponseEntity<ErrorResponse> {
        logger.error("Entity not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(HttpStatus.NOT_FOUND.value(), e.message))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        logger.error("Illegal State: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(HttpStatus.CONFLICT.value(), e.message ?: "Illegal state"))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled Exception: ", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error occurred"))
    }
}
