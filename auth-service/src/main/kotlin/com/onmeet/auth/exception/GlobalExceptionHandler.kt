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

    @ExceptionHandler(EmailAlreadyExistsException::class, TeamAlreadyExistsException::class, ActiveInvitationExistsException::class, CompanyAlreadyExistsException::class)
    fun handleConflictException(e: RuntimeException): ResponseEntity<ErrorResponse> {
        logger.error("Conflict error: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(HttpStatus.CONFLICT.value(), e.message))
    }

    @ExceptionHandler(InsufficientPermissionException::class, CrossCompanyAccessException::class)
    fun handleForbiddenException(e: RuntimeException): ResponseEntity<ErrorResponse> {
        logger.warn("Forbidden access: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(HttpStatus.FORBIDDEN.value(), e.message))
    }

    @ExceptionHandler(InvalidInvitationException::class, InvalidTokenException::class, CompanyMismatchException::class)
    fun handleBadRequestException(e: RuntimeException): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.message))
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException): ResponseEntity<ErrorResponse> {
        logger.error("Authentication failed: {}", e.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.message ?: "Authentication failed"))
    }
}