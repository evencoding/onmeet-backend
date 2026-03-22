package com.onmeet.ai.dto.event;

import lombok.*;
import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceSegmentCreatedEvent {

    private Long roomId;
    private String segmentId;

    /** 발화자 회원 ID (회원인 경우에만 존재, 비회원은 null) */
    private Long participantId;

    /** 발화자 표시 이름 (회원/비회원 모두 사용자가 설정한 이름) */
    private String participantName;

    private long segmentStartMs;
    private long segmentEndMs;

    private long seq;

    private String text;

    private Instant timestamp;
}
