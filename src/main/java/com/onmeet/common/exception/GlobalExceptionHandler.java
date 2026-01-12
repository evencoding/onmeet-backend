package com.onmeet.common.exception;

import com.onmeet.common.response.ApiResponse;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ApiErrorResponse response = new ApiErrorResponse(
            errorCode.name(),
            ex.getMessage(),
            Instant.now(),
            List.of()
        );
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(response));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.FieldError> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::toFieldError)
            .collect(Collectors.toList());
        ApiErrorResponse response = new ApiErrorResponse(
            ErrorCode.INVALID_REQUEST.name(),
            ErrorCode.INVALID_REQUEST.getMessage(),
            Instant.now(),
            errors
        );
        return ResponseEntity.badRequest().body(ApiResponse.error(response));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            ErrorCode.INTERNAL_ERROR.name(),
            ErrorCode.INTERNAL_ERROR.getMessage(),
            Instant.now(),
            List.of()
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus()).body(ApiResponse.error(response));
    }

    private ApiErrorResponse.FieldError toFieldError(FieldError fieldError) {
        String message = fieldError.getDefaultMessage() == null ? "Invalid" : fieldError.getDefaultMessage();
        return new ApiErrorResponse.FieldError(fieldError.getField(), message);
    }
}
