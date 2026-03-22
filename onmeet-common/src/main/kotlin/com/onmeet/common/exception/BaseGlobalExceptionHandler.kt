package com.onmeet.common.exception

import com.onmeet.common.response.ApiResponse
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
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = e.errorCode
        logger.warn("[{}] {}", errorCode.code, errorCode.message)
        return ResponseEntity.status(errorCode.status)
            .body(ApiResponse.error(errorCode.status.value(), errorCode.message, errorCode.code))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("Illegal Argument: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), e.message ?: "잘못된 요청입니다", "COMMON_BAD_REQUEST"))
    }

    @ExceptionHandler(InsufficientPermissionException::class)
    fun handleInsufficientPermissionException(e: InsufficientPermissionException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("Insufficient permission: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), e.message, "COMMON_FORBIDDEN"))
    }

    @ExceptionHandler(CrossCompanyAccessException::class)
    fun handleCrossCompanyAccessException(e: CrossCompanyAccessException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("Cross company access denied: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), e.message, "COMMON_CROSS_COMPANY"))
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDeniedException(e: org.springframework.security.access.AccessDeniedException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("Access denied: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "접근이 거부되었습니다", "COMMON_ACCESS_DENIED"))
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFoundException(e: EntityNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("Entity not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), e.message, "COMMON_NOT_FOUND"))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Illegal State: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(HttpStatus.CONFLICT.value(), e.message ?: "잘못된 상태입니다", "COMMON_CONFLICT"))
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException::class)
    fun handleValidationException(e: org.springframework.web.bind.MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errorMessage = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn("Validation failed: {}", errorMessage)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "입력값 검증 실패: $errorMessage", "COMMON_VALIDATION_FAILED"))
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupportedException(e: org.springframework.web.HttpRequestMethodNotSupportedException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("Method not supported: {} for this endpoint. Supported: {}", e.method, e.supportedHttpMethods)
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ApiResponse.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "'${e.method}' 메서드는 지원하지 않습니다", "COMMON_METHOD_NOT_ALLOWED"))
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException::class)
    fun handleNoHandlerFoundException(e: org.springframework.web.servlet.NoHandlerFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("No handler found: {} {}", e.httpMethod, e.requestURL)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "요청한 리소스를 찾을 수 없습니다", "COMMON_NOT_FOUND"))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Unhandled Exception: ", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버 내부 오류가 발생했습니다", "COMMON_INTERNAL_ERROR"))
    }
}
