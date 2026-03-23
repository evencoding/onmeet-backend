package com.onmeet.video.meeting.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.common.dto.event.ChatMessageEvent;
import com.onmeet.video.meeting.event.participant.ParticipantEvent;
import com.onmeet.common.dto.event.AudioChunkReadyEvent;
import com.onmeet.video.meeting.event.room.MeetingEvent;
import com.onmeet.video.meeting.event.screenshare.ScreenShareEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 실제 Kafka 이벤트 발행 구현체.
 * 로컬 환경을 제외한 프로파일(chat, dev, prod)에서 활성화된다.
 *
 * 발행 토픽:
 * - meeting-ended: 회의 종료 이벤트 → ai-service MeetingEndedConsumer가 소비
 * - chat-events: 채팅 메시지 이벤트
 * - audio-chunk-ready: 오디오 세그먼트 준비 이벤트 → ai-service STT 처리
 */
// CHECK [video-담당자]: KafkaMeetingEventPublisher - meeting-ended 토픽 메시지 스키마가
// ai-service MeetingEndedConsumer와 일치하는지 확인 필요. 특히 roomId, participants 필드.
// CHECK [video-담당자]: audio-chunk-ready 토픽 스키마가 ai-service AudioChunkConsumer와 일치하는지
// 확인 필요. AudioChunkReadyEvent 필드(roomId, participantId, participantName, s3Path, fileId, segmentIndex)를 검증할 것.
// CHECK [video-담당자]: 실제 Kafka 발행은 kafka.enabled=true 설정 시에만 활성화
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class KafkaMeetingEventPublisher implements MeetingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMeetingEventPublisher.class);

    static final String TOPIC_MEETING_ENDED = "meeting.ended";
    static final String TOPIC_CHAT_EVENTS = "chat.events";
    static final String TOPIC_AUDIO_CHUNK_READY = "audio.chunk.ready";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaMeetingEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                      ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishMeetingStarted(MeetingEvent event) {
        log.info("Meeting started: roomId={}, hostUserId={}", event.roomId(), event.hostUserId());
    }

    @Override
    public void publishMeetingEnded(MeetingEvent event) {
        // CHECK [video-담당자]: meeting-ended 토픽 - ai-service에서 회의 요약 트리거로 사용.
        // 스키마 불일치 시 ai-service 요약 기능 장애 발생.
        publish(TOPIC_MEETING_ENDED, String.valueOf(event.roomId()), event);
    }

    @Override
    public void publishParticipantJoined(ParticipantEvent event) {
        log.info("Participant joined: roomId={}, userId={}", event.roomId(), event.userId());
    }

    @Override
    public void publishParticipantLeft(ParticipantEvent event) {
        log.info("Participant left: roomId={}, userId={}", event.roomId(), event.userId());
    }

    @Override
    public void publishAudioSegmentReady(AudioChunkReadyEvent event) {
        publish(TOPIC_AUDIO_CHUNK_READY, String.valueOf(event.getRoomId()), event);
    }

    @Override
    public void publishChatMessage(ChatMessageEvent event) {
        publish(TOPIC_CHAT_EVENTS, event.getMessageId(), event);
    }

    @Override
    public void publishScreenShareStarted(ScreenShareEvent event) {
        log.info("Screen share started: roomId={}, userId={}", event.roomId(), event.userId());
    }

    @Override
    public void publishScreenShareStopped(ScreenShareEvent event) {
        log.info("Screen share stopped: roomId={}, userId={}", event.roomId(), event.userId());
    }

    private void publish(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka publish failed: topic={}, key={}, error={}", topic, key, ex.getMessage());
                        } else {
                            log.debug("Kafka published: topic={}, key={}, offset={}",
                                    topic, key, result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka event: topic={}, key={}, error={}", topic, key, e.getMessage());
        }
    }
}
