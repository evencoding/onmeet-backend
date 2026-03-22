package com.onmeet.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 서비스 간 공유되는 채팅 메시지 이벤트 DTO
 *
 * - video-service : Kafka 발행 측
 * - ai-service    : Kafka 수신 측
 *
 * senderId는 회원인 경우에만 존재하며, 비회원인 경우 null입니다.
 * senderName은 회원/비회원 모두 사용자가 설정한 표시 이름이며,
 * AudioChunkReadyEvent.participantName 와 동일한 역할입니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEvent {

    /** 메시지 고유 ID */
    private String messageId;

    /** 회의실 ID */
    private Long roomId;

    /** LiveKit 회의실 이름 */
    private String roomName;

    /**
     * 발신자 회원 ID
     * 회원인 경우에만 존재. 비회원은 null.
     */
    private Long senderId;

    /**
     * 발신자 표시 이름 (회원/비회원 모두 사용자가 설정한 이름)
     * AudioChunkReadyEvent.participantName 와 대응됩니다.
     */
    private String senderName;

    /** 메시지 타입 (CHAT, SYSTEM 등) */
    private String messageType;

    /** 메시지 내용 */
    private String content;

    /** 답장 대상 메시지 ID (없으면 null) */
    private String replyToMessageId;

    /** ai-service Redis 저장 시 시간 내 정렬 기준 */
    private Long seq;

    /** 이벤트 발생 시각 */
    private Instant timestamp;
}
