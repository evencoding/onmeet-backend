package com.onmeet.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.ChatMessageEvent;
import com.onmeet.ai.dto.event.TranscriptFinalizedEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.messaging.producer.TranscriptEventsProducer;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.storage.StorageKeyFactory;
import com.onmeet.ai.pipeline.transcript.RedisMeetingEventStore;
import com.onmeet.ai.pipeline.transcript.TranscriptDocument;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TranscriptBuilderService {

    private final RedisMeetingEventStore store;
    private final StorageClient storageClient;
    private final TranscriptEventsProducer producer;
    private final ObjectMapper om;

    public TranscriptBuilderService(
            RedisMeetingEventStore store,
            StorageClient storageClient,
            TranscriptEventsProducer producer,
            ObjectMapper om
    ) {
        this.store = store;
        this.storageClient = storageClient;
        this.producer = producer;
        this.om = om;
    }

    public void ingestChat(ChatMessageEvent event) {
        store.appendChat(event);
    }

    public void ingestVoice(VoiceSegmentCreatedEvent event) {
        store.appendVoice(event);
    }

    /**
     * ✅ endedAtEpochMs를 "정말 쓸 거면" TranscriptDocument에 메타로 넣는 게 맞고,
     * 지금처럼 쓰지 않을 거면 파라미터 제거가 맞음.
     * 여기서는 일단 파라미터는 유지하되, doc 메타에 포함시키는 방향으로 정리해 둠.
     */
    public void finalizeMeeting(String meetingId, long endedAtEpochMs) {
        List<RedisMeetingEventStore.StoredEvent> items = store.readAll(meetingId);

        String transcriptId = UUID.randomUUID().toString();
        int version = 1;

        List<TranscriptDocument.Event> events = new ArrayList<>(items.size());
        for (RedisMeetingEventStore.StoredEvent it : items) {
            if ("CHAT".equals(it.getType())) {
                ChatMessageEvent e = read(it.getJson(), ChatMessageEvent.class);
                events.add(TranscriptDocument.Event.builder()
                        .id(e.getMessageId())
                        .type("CHAT")
                        .actorId(e.getSenderId())
                        .atMs(e.getAtMs())
                        .seq(e.getSeq())
                        .text(e.getContent())
                        .build());
            } else if ("VOICE".equals(it.getType())) {
                VoiceSegmentCreatedEvent e = read(it.getJson(), VoiceSegmentCreatedEvent.class);
                events.add(TranscriptDocument.Event.builder()
                        .id(e.getSegmentId())
                        .type("VOICE")
                        .actorId(e.getParticipantId())
                        .atMs(e.getStartMs())
                        .seq(e.getSeq())
                        .text(e.getText())
                        .startMs(e.getStartMs())
                        .endMs(e.getEndMs())
                        .build());
            }
        }

        // ✅ 안전 정렬: atMs → seq
        events = events.stream()
                .sorted(Comparator.comparingLong(TranscriptDocument.Event::getAtMs)
                        .thenComparingLong(TranscriptDocument.Event::getSeq))
                .collect(Collectors.toList());

        // ✅ TranscriptDocument에 endedAtEpochMs를 직접 넣을 필드가 없다면,
        // (1) transcript document에 meta 필드 추가하거나
        // (2) 일단 finalized 이벤트에만 넣고 doc에는 안 넣어도 됨.
        TranscriptDocument doc = TranscriptDocument.builder()
                .meetingId(meetingId)
                .transcriptId(transcriptId)
                .version(version)
                .events(events)
                .build();

        // ✅ key 하드코딩 제거
        String s3Key = StorageKeyFactory.transcriptKey(meetingId, transcriptId);
        storageClient.writeText(s3Key, write(doc), "application/json");

        producer.publish(TranscriptFinalizedEvent.builder()
                .meetingId(meetingId)
                .transcriptId(transcriptId)
                .transcriptS3Key(s3Key)
                .version(version)
                .finalizedAtEpochMs(Instant.now().toEpochMilli())
                .build());

        // ✅ 정리 (메서드명은 네 store 구현에 맞게 통일!)
        // store.clearMeeting(meetingId);  // 현재 네 코드
        store.clearMeeting(meetingId);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return om.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse stored event json: " + type.getSimpleName(), e);
        }
    }

    private String write(Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize transcript json", e);
        }
    }
}
