package com.onmeet.auth.exception

import com.onmeet.common.exception.BaseGlobalExceptionHandler
import com.onmeet.common.exception.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler : BaseGlobalExceptionHandler() {

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailAlreadyExistsException(e: EmailAlreadyExistsException): ResponseEntity<ErrorResponse> {
        logger.error("Email already exists: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(HttpStatus.CONFLICT.value(), e.message))
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException): ResponseEntity<ErrorResponse> {
        logger.error("Authentication failed: {}", e.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.message ?: "Authentication failed"))
    }
}