package com.onmeet.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.MinutesGeneratedEvent;
import com.onmeet.ai.dto.event.TranscriptFinalizedEvent;
import com.onmeet.ai.entity.Minutes;
import com.onmeet.ai.messaging.producer.MinutesEventsProducer;
import com.onmeet.ai.pipeline.nlp.SummarizerClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.storage.StorageKeyFactory;
import com.onmeet.ai.pipeline.transcript.TranscriptDocument;
import com.onmeet.ai.pipeline.transcript.TranscriptRenderer;
import com.onmeet.ai.repository.MinutesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SummaryWorkerService {

    private final StorageClient storageClient;
    private final ObjectMapper om;
    private final TranscriptRenderer renderer;
    private final SummarizerClient summarizerClient;
    private final MinutesRepository minutesRepository;
    private final MinutesEventsProducer producer;

    public SummaryWorkerService(
            StorageClient storageClient,
            ObjectMapper om,
            TranscriptRenderer renderer,
            SummarizerClient summarizerClient,
            MinutesRepository minutesRepository,
            MinutesEventsProducer producer
    ) {
        this.storageClient = storageClient;
        this.om = om;
        this.renderer = renderer;
        this.summarizerClient = summarizerClient;
        this.minutesRepository = minutesRepository;
        this.producer = producer;
    }

    public void handleTranscriptFinalized(TranscriptFinalizedEvent e) {

        String transcriptJson = storageClient.readText(e.getTranscriptS3Key());

        TranscriptDocument doc;
        try {
            doc = om.readValue(transcriptJson, TranscriptDocument.class);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse transcript json: " + e.getTranscriptS3Key(), ex);
        }

        String plain = renderer.toPlainText(doc);
        if (plain == null || plain.isBlank()) {
            throw new IllegalStateException("empty transcript");
        }

        String summaryJson = summarizerClient.summarize(plain, "ko", "default", "claude-sonnet");

        String summaryS3Key = StorageKeyFactory.summaryKey(e.getRoomId(), e.getTranscriptId());
        storageClient.writeText(summaryS3Key, summaryJson, "application/json");

        upsertMinutes(
                e.getRoomId(),
                e.getTranscriptId(),
                e.getTranscriptS3Key(),
                summaryS3Key,
                summaryJson
        );

        producer.publish(MinutesGeneratedEvent.builder()
                .roomId(e.getRoomId())
                .transcriptId(e.getTranscriptId())
                .transcriptS3Key(e.getTranscriptS3Key())
                .generatedAt(Instant.now())
                .build());
    }

    @Transactional
    protected void upsertMinutes(
            Long roomId,
            String transcriptId,
            String transcriptS3Key,
            String summaryS3Key,
            String summaryJson
    ) {
        Minutes m = minutesRepository.findByRoomId(roomId).orElse(null);

        if (m == null) {
            minutesRepository.save(
                    Minutes.createGenerated(roomId, transcriptId, transcriptS3Key, summaryS3Key, summaryJson)
            );
            return;
        }

        m.applyGenerated(transcriptId, transcriptS3Key, summaryS3Key, summaryJson);
        minutesRepository.save(m);
    }
}
