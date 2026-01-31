package com.onmeet.chat.dto;

import com.onmeet.chat.exception.ChatErrorCode;
import com.onmeet.chat.exception.ChatException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatSendRequestValidator {

    public void validate(ChatSendRequestDto req) {
        String type = (req.messageType() == null)
                ? ""
                : req.messageType().trim().toUpperCase();

        if ("SYSTEM".equals(type)) {
            throw new ChatException(ChatErrorCode.SYSTEM_MESSAGE_NOT_ALLOWED);
        }

        if ("TEXT".equals(type)) {
            if (!StringUtils.hasText(req.content())) {
                throw new ChatException(ChatErrorCode.TEXT_CONTENT_REQUIRED);
            }
            if (req.attachmentJson() != null) {
                throw new ChatException(ChatErrorCode.ATTACHMENT_NOT_ALLOWED_FOR_TEXT);
            }
            return;
        }

        if (!("IMAGE".equals(type) || "FILE".equals(type) || "PDF".equals(type))) {
            throw new ChatException(
                    ChatErrorCode.INVALID_MESSAGE_TYPE,
                    "Unsupported messageType: " + req.messageType()
            );
        }

        if (!StringUtils.hasText(req.attachmentJson())) {
            throw new ChatException(ChatErrorCode.ATTACHMENT_REQUIRED);
        }
    }
}
