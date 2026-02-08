package com.onmeet.file.exception

import com.onmeet.common.exception.BaseGlobalExceptionHandler
import com.onmeet.common.exception.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler : BaseGlobalExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(FileUploadException::class)
    fun handleFileUploadException(e: FileUploadException): ResponseEntity<ErrorResponse> {
        log.error("File Upload Failed: {}", e.message)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.message ?: "Unknown error"))
    }
}
