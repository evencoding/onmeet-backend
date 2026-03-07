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

    /**
     * transcript.finalized 이벤트를 받아 요약 생성 + minutes upsert + minutes.generated 발행
     */
    public void handleTranscriptFinalized(TranscriptFinalizedEvent e) {

        // 1) transcript 로드
        String transcriptJson = storageClient.readText(e.getTranscriptS3Key());

        TranscriptDocument doc;
        try {
            doc = om.readValue(transcriptJson, TranscriptDocument.class);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse transcript json: " + e.getTranscriptS3Key(), ex);
        }

        // 2) plain text 변환
        String plain = renderer.toPlainText(doc);
        if (plain == null || plain.isBlank()) {
            throw new IllegalStateException("empty transcript");
        }

        // 3) 요약 생성 (v1 기본값)
        String summaryJson = summarizerClient.summarize(plain, "ko", "default", "claude-sonnet");

        // 4) summary S3 저장 (권장)
        String summaryS3Key = StorageKeyFactory.summaryKey(e.getMeetingId(), e.getTranscriptId());
        storageClient.writeText(summaryS3Key, summaryJson, "application/json");

        // 5) minutes upsert (DB)
        upsertMinutes(
                e.getMeetingId(),
                e.getTranscriptId(),
                e.getTranscriptS3Key(),
                summaryS3Key,
                summaryJson
        );

        // 6) minutes.generated 발행
        producer.publish(MinutesGeneratedEvent.builder()
                .meetingId(e.getMeetingId())
                .transcriptId(e.getTranscriptId())
                .transcriptS3Key(e.getTranscriptS3Key())
                // (원하면 event에 summaryS3Key 필드 추가해서 함께 발행 추천)
                .generatedAtEpochMs(Instant.now().toEpochMilli())
                .build());
    }

    @Transactional
    protected void upsertMinutes(
            String meetingId,
            String transcriptId,
            String transcriptS3Key,
            String summaryS3Key,
            String summaryJson
    ) {
        Minutes m = minutesRepository.findById(meetingId).orElse(null);

        if (m == null) {
            minutesRepository.save(
                    Minutes.createGenerated(meetingId, transcriptId, transcriptS3Key, summaryS3Key, summaryJson)
            );
            return;
        }

        m.applyGenerated(transcriptId, transcriptS3Key, summaryS3Key, summaryJson);
        minutesRepository.save(m);
    }
}
