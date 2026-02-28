package com.onmeet.auth.exception

import com.onmeet.common.dto.ErrorResponse
import com.onmeet.common.exception.BaseGlobalExceptionHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler : BaseGlobalExceptionHandler() {

    @ExceptionHandler(EmailAlreadyExistsException::class, TeamAlreadyExistsException::class, ActiveInvitationExistsException::class, CompanyAlreadyExistsException::class)
    fun handleConflictException(e: RuntimeException): ResponseEntity<ErrorResponse> {
        logger.error("Conflict error: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(HttpStatus.CONFLICT.value(), e.message ?: "Conflict occurred"))
    }
    @ExceptionHandler(InvalidInvitationException::class, InvalidTokenException::class, CompanyMismatchException::class)
    fun handleBadRequestException(e: RuntimeException): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.message ?: "Invalid request"))
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException): ResponseEntity<ErrorResponse> {
        logger.error("Authentication failed: {}", e.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.message ?: "Authentication failed"))
    }
}