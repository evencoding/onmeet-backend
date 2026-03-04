package com.onmeet.ai.dto.event;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceSegmentCreatedEvent {

    private String meetingId;
    private String segmentId;

    // 트랙/사용자 식별
    private String participantId;  // 누가 말했는지(트랙 주인)
    private String trackId;        // 있으면 저장(없으면 제거 가능)

    // 타임라인
    private long startMs;
    private long endMs;

    // 정렬 보조키(동일 ms일 때 순서 보장)
    private long seq;

    // STT 결과
    private String text;

    private long occurredAtEpochMs;
}
