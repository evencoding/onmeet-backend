package com.onmeet.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.common.dto.event.ChatMessageEvent;
import com.onmeet.ai.dto.event.TranscriptFinalizedEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.entity.Transcript;
import com.onmeet.ai.entity.TranscriptEvent;
import com.onmeet.ai.messaging.producer.TranscriptEventsProducer;
import com.onmeet.ai.pipeline.transcript.RedisMeetingEventStore;
import com.onmeet.ai.pipeline.transcript.TranscriptDocument;
import com.onmeet.ai.repository.TranscriptEventRepository;
import com.onmeet.ai.repository.TranscriptRepository;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.AiErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TranscriptBuilderService {

    private final RedisMeetingEventStore store;
    private final TranscriptRepository transcriptRepository;
    private final TranscriptEventRepository transcriptEventRepository;
    private final TranscriptEventsProducer producer;
    private final ObjectMapper om;

    public TranscriptBuilderService(
            RedisMeetingEventStore store,
            TranscriptRepository transcriptRepository,
            TranscriptEventRepository transcriptEventRepository,
            TranscriptEventsProducer producer,
            ObjectMapper om
    ) {
        this.store = store;
        this.transcriptRepository = transcriptRepository;
        this.transcriptEventRepository = transcriptEventRepository;
        this.producer = producer;
        this.om = om;
    }

    public void ingestChat(ChatMessageEvent event) {
        store.appendChat(event);
    }

    public void ingestVoice(VoiceSegmentCreatedEvent event) {
        store.appendVoice(event);
    }

    @Transactional
    public void finalizeMeeting(Long roomId, Long hostUserId, Instant endedAt) {
        List<RedisMeetingEventStore.StoredEvent> items = store.readAll(roomId);

        String transcriptId = UUID.randomUUID().toString();
        int version = 1;

        // Redis 이벤트 → TranscriptEvent 엔티티 목록으로 변환
        List<TranscriptEvent> eventEntities = new ArrayList<>(items.size());
        for (RedisMeetingEventStore.StoredEvent it : items) {
            if ("CHAT".equals(it.getType())) {
                ChatMessageEvent e = read(it.getJson(), ChatMessageEvent.class);
                eventEntities.add(TranscriptEvent.builder()
                        .transcriptId(transcriptId)
                        .eventId(e.getMessageId())
                        .type("CHAT")
                        .participantId(e.getSenderId() != null ? String.valueOf(e.getSenderId()) : null)
                        .participantName(e.getSenderName())
                        .timestamp(e.getTimestamp())
                        .seq(e.getSeq() != null ? e.getSeq() : 0L)
                        .text(e.getContent())
                        .build());
            } else if ("VOICE".equals(it.getType())) {
                VoiceSegmentCreatedEvent e = read(it.getJson(), VoiceSegmentCreatedEvent.class);
                eventEntities.add(TranscriptEvent.builder()
                        .transcriptId(transcriptId)
                        .eventId(e.getSegmentId())
                        .type("VOICE")
                        .participantId(e.getParticipantId() != null ? String.valueOf(e.getParticipantId()) : null)
                        .participantName(e.getParticipantName())
                        .timestamp(e.getTimestamp() != null ? e.getTimestamp() : Instant.ofEpochMilli(e.getSegmentStartMs()))
                        .seq(e.getSeq())
                        .text(e.getText())
                        .segmentStartMs(e.getSegmentStartMs())
                        .segmentEndMs(e.getSegmentEndMs())
                        .build());
            }
        }

        // 시간순 정렬
        eventEntities.sort(Comparator.comparing((TranscriptEvent ev) ->
                ev.getTimestamp() != null ? ev.getTimestamp().toEpochMilli() : 0L)
                .thenComparingLong(ev -> ev.getSeq() != null ? ev.getSeq() : 0L));

        // DB 저장: transcript 헤더 + transcript_event 배치 Insert
        transcriptRepository.save(Transcript.create(roomId, transcriptId, version));
        transcriptEventRepository.saveAll(eventEntities);

        producer.publish(TranscriptFinalizedEvent.builder()
                .roomId(roomId)
                .hostUserId(hostUserId)
                .transcriptId(transcriptId)
                .transcriptS3Key(null)  // DB 전환 후 불필요, null 처리
                .version(version)
                .finalizedAt(Instant.now())
                .build());

        store.clearMeeting(roomId);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return om.readValue(json, type);
        } catch (Exception e) {
            // TODO: [AI][AiErrorCode.REDIS_EVENT_PARSE_FAILED] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.REDIS_EVENT_PARSE_FAILED);
        }
    }

    private String write(Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (Exception e) {
            // TODO: [AI][AiErrorCode.TRANSCRIPT_SERIALIZE_FAILED] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.TRANSCRIPT_SERIALIZE_FAILED);
        }
    }
}
