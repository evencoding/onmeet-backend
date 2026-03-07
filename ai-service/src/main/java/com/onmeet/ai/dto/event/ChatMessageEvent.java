package com.onmeet.ai.dto.event;

import lombok.*;

@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessageEvent {
    private String meetingId;
    private String messageId;
    private String senderId;

    // meeting 기준 상대 ms
    private long atMs;

    // tie-breaker
    private long seq;

    private String content;
    private long occurredAtEpochMs;
}
