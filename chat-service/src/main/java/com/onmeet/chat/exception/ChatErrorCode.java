package com.onmeet.chat.exception;

public enum ChatErrorCode {

    INVALID_MESSAGE_TYPE("Invalid message type."),
    SYSTEM_MESSAGE_NOT_ALLOWED("SYSTEM messageType is server-only."),
    TEXT_CONTENT_REQUIRED("TEXT message requires content."),
    ATTACHMENT_NOT_ALLOWED_FOR_TEXT("TEXT message must not include attachment."),
    ATTACHMENT_REQUIRED("Attachment is required for this message type.");

    private final String message;

    ChatErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
