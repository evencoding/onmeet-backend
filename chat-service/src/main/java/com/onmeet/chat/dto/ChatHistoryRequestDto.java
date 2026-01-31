package com.onmeet.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChatHistoryRequestDto(

        @NotNull
        Long meetRoomId,

         // 첫 로딩이면 null
         // 더보기면 "이 id보다 작은(과거)" 메시지를 조회
        Long beforeId,

        @Min(1)
        @Max(200)
        Integer size
) {
    public int sizeOrDefault() {
        return (size == null) ? 50 : size;
    }
}
