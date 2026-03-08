package com.onmeet.ai.dto.event;

import lombok.*;
import java.time.Instant;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessageEvent {
    private Long roomId;
    private String messageId;
    private Long senderId;

    private Instant timestamp;
    private long seq;

    private String content;
}
