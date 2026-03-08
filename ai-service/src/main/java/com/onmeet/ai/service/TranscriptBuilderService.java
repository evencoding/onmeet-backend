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

    public void finalizeMeeting(Long roomId, Instant endedAt) {
        List<RedisMeetingEventStore.StoredEvent> items = store.readAll(roomId);

        String transcriptId = UUID.randomUUID().toString();
        int version = 1;

        List<TranscriptDocument.Event> events = new ArrayList<>(items.size());
        for (RedisMeetingEventStore.StoredEvent it : items) {
            if ("CHAT".equals(it.getType())) {
                ChatMessageEvent e = read(it.getJson(), ChatMessageEvent.class);
                events.add(TranscriptDocument.Event.builder()
                        .id(e.getMessageId())
                        .type("CHAT")
                        .actorId(String.valueOf(e.getSenderId()))
                        .timestamp(e.getTimestamp())
                        .seq(e.getSeq())
                        .text(e.getContent())
                        .build());
            } else if ("VOICE".equals(it.getType())) {
                VoiceSegmentCreatedEvent e = read(it.getJson(), VoiceSegmentCreatedEvent.class);
                events.add(TranscriptDocument.Event.builder()
                        .id(e.getSegmentId())
                        .type("VOICE")
                        .actorId(e.getParticipantIdentity())
                        // voice event does not have absolute audio timestamp yet, using occurred timestamp or startMs mapping
                        .timestamp(e.getTimestamp() != null ? e.getTimestamp() : Instant.ofEpochMilli(e.getSegmentStartMs()))
                        .seq(e.getSeq())
                        .text(e.getText())
                        .segmentStartMs(e.getSegmentStartMs())
                        .segmentEndMs(e.getSegmentEndMs())
                        .build());
            }
        }

        events = events.stream()
                .sorted(Comparator.comparing((TranscriptDocument.Event e) -> e.getTimestamp() != null ? e.getTimestamp().toEpochMilli() : 0L)
                        .thenComparingLong(TranscriptDocument.Event::getSeq))
                .collect(Collectors.toList());

        TranscriptDocument doc = TranscriptDocument.builder()
                .roomId(roomId)
                .transcriptId(transcriptId)
                .version(version)
                .events(events)
                .build();

        String s3Key = StorageKeyFactory.transcriptKey(roomId, transcriptId);
        storageClient.writeText(s3Key, write(doc), "application/json");

        producer.publish(TranscriptFinalizedEvent.builder()
                .roomId(roomId)
                .transcriptId(transcriptId)
                .transcriptS3Key(s3Key)
                .version(version)
                .finalizedAt(Instant.now())
                .build());

        store.clearMeeting(roomId);
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
