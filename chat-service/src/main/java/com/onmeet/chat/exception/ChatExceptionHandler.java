package com.onmeet.chat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ChatExceptionHandler {

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ChatErrorResponse> handleChatException(ChatException e) {
        return ResponseEntity
                .badRequest()
                .body(new ChatErrorResponse(
                        e.getErrorCode().name(),
                        e.getMessage()
                ));
    }
}
