package com.onmeet.file.exception

import com.onmeet.common.dto.ErrorResponse
import com.onmeet.common.exception.BaseGlobalExceptionHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler : BaseGlobalExceptionHandler() {

    @ExceptionHandler(FileUploadException::class)
    fun handleFileUploadException(e: FileUploadException): ResponseEntity<ErrorResponse> {
        logger.error("File upload error: {}", e.message)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.message ?: "File upload failed"))
    }
}
