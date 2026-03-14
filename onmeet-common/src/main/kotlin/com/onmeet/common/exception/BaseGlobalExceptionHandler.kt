package com.onmeet.common.exception

import com.onmeet.common.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * 모든 마이크로서비스에서 상속받아 사용할 기본 전역 예외 핸들러.
 *
 * BusinessException을 최우선으로 처리하며, 기존 예외 타입도 하위 호환을 위해 유지한다.
 */
abstract class BaseGlobalExceptionHandler {
    protected val logger = LoggerFactory.getLogger(this.javaClass)

    /**
     * BusinessException 핸들러 (최우선).
     * ErrorCode에 정의된 code, status, message를 그대로 응답한다.
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        val errorCode = e.errorCode
        logger.warn("[{}] {}", errorCode.code, errorCode.message)
        return ResponseEntity.status(errorCode.status)
            .body(ErrorResponse(code = errorCode.code, status = errorCode.status.value(), message = errorCode.message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal Argument: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "COMMON_BAD_REQUEST", status = HttpStatus.BAD_REQUEST.value(), message = e.message ?: "잘못된 요청입니다"))
    }

    @ExceptionHandler(InsufficientPermissionException::class)
    fun handleInsufficientPermissionException(e: InsufficientPermissionException): ResponseEntity<ErrorResponse> {
        logger.warn("Insufficient permission: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(code = "COMMON_FORBIDDEN", status = HttpStatus.FORBIDDEN.value(), message = e.message))
    }

    @ExceptionHandler(CrossCompanyAccessException::class)
    fun handleCrossCompanyAccessException(e: CrossCompanyAccessException): ResponseEntity<ErrorResponse> {
        logger.warn("Cross company access denied: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(code = "COMMON_CROSS_COMPANY", status = HttpStatus.FORBIDDEN.value(), message = e.message))
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDeniedException(e: org.springframework.security.access.AccessDeniedException): ResponseEntity<ErrorResponse> {
        logger.warn("Access denied: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(code = "COMMON_ACCESS_DENIED", status = HttpStatus.FORBIDDEN.value(), message = "접근이 거부되었습니다"))
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFoundException(e: EntityNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Entity not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(code = "COMMON_NOT_FOUND", status = HttpStatus.NOT_FOUND.value(), message = e.message))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        logger.error("Illegal State: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(code = "COMMON_CONFLICT", status = HttpStatus.CONFLICT.value(), message = e.message ?: "잘못된 상태입니다"))
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException::class)
    fun handleValidationException(e: org.springframework.web.bind.MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorMessage = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn("Validation failed: {}", errorMessage)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "COMMON_VALIDATION_FAILED", status = HttpStatus.BAD_REQUEST.value(), message = "입력값 검증 실패: $errorMessage"))
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupportedException(e: org.springframework.web.HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        logger.warn("Method not supported: {} for this endpoint. Supported: {}", e.method, e.supportedHttpMethods)
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ErrorResponse(code = "COMMON_METHOD_NOT_ALLOWED", status = HttpStatus.METHOD_NOT_ALLOWED.value(), message = "'${e.method}' 메서드는 지원하지 않습니다"))
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException::class)
    fun handleNoHandlerFoundException(e: org.springframework.web.servlet.NoHandlerFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("No handler found: {} {}", e.httpMethod, e.requestURL)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(code = "COMMON_NOT_FOUND", status = HttpStatus.NOT_FOUND.value(), message = "요청한 리소스를 찾을 수 없습니다"))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled Exception: ", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(code = "COMMON_INTERNAL_ERROR", status = HttpStatus.INTERNAL_SERVER_ERROR.value(), message = "서버 내부 오류가 발생했습니다"))
    }
}
