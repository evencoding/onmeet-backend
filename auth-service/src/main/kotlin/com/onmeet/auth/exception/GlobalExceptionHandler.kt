package com.onmeet.auth.exception

import com.onmeet.common.exception.BaseGlobalExceptionHandler
import com.onmeet.common.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler : BaseGlobalExceptionHandler() {

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Authentication failed: {}", e.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), e.message ?: "Authentication failed", "AUTH_004"))
    }
}