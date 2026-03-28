package com.onmeet.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SttChunkCompletedEvent {
    private Long roomId;
    private int segmentIndex;
    private Long participantId;
}
