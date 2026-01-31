package com.onmeet.chat.exception;

public class ChatException extends RuntimeException {

    private final ChatErrorCode errorCode;

    public ChatException(ChatErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ChatException(ChatErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    public ChatErrorCode getErrorCode() {
        return errorCode;
    }
}
