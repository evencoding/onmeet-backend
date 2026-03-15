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
import com.onmeet.ai.messaging.producer.NotificationEventPublisher;
import com.onmeet.common.dto.NotificationRequestDto;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.AiErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SummaryWorkerService {

    // CHECK [ai-담당자]: AI 시스템 알림의 actorUserId를 0L(시스템 행위자)로 설정.
    // notification-service에서 actorUserId=0 이면 시스템 발송으로 처리되는지 확인 필요.
    private static final Long SYSTEM_ACTOR_ID = 0L;

    private final StorageClient storageClient;
    private final ObjectMapper om;
    private final TranscriptRenderer renderer;
    private final SummarizerClient summarizerClient;
    private final MinutesRepository minutesRepository;
    private final MinutesEventsProducer producer;
    private final NotificationEventPublisher notificationEventPublisher;

    public SummaryWorkerService(
            StorageClient storageClient,
            ObjectMapper om,
            TranscriptRenderer renderer,
            SummarizerClient summarizerClient,
            MinutesRepository minutesRepository,
            MinutesEventsProducer producer,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.storageClient = storageClient;
        this.om = om;
        this.renderer = renderer;
        this.summarizerClient = summarizerClient;
        this.minutesRepository = minutesRepository;
        this.producer = producer;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    public void handleTranscriptFinalized(TranscriptFinalizedEvent e) {
        // AI 요약 진행 중 알림 (Kafka 비동기)
        notificationEventPublisher.publishNotification(
            new NotificationRequestDto(
                e.getHostUserId(), null, "AI_SUMMARY_PROGRESS", "AI 요약 시작",
                "회의록 AI 요약이 시작되었습니다.",
                "/meeting/" + e.getRoomId() + "?tab=minutes", "MEETING", String.valueOf(e.getRoomId()), SYSTEM_ACTOR_ID,
                null, null
            )
        );

        String transcriptJson = storageClient.readText(e.getTranscriptS3Key());

        TranscriptDocument doc;
        try {
            doc = om.readValue(transcriptJson, TranscriptDocument.class);
        } catch (Exception ex) {
            // TODO: [AI][AiErrorCode.TRANSCRIPT_PARSE_FAILED] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.TRANSCRIPT_PARSE_FAILED);
        }

        String plain = renderer.toPlainText(doc);
        if (plain == null || plain.isBlank()) {
            // TODO: [AI][AiErrorCode.TRANSCRIPT_EMPTY] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.TRANSCRIPT_EMPTY);
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

        // AI 요약 완료 알림 (Kafka 비동기)
        notificationEventPublisher.publishNotification(
            new NotificationRequestDto(
                e.getHostUserId(), null, "AI_SUMMARY_COMPLETED", "AI 요약 완료",
                "회의록 AI 요약이 완료되었습니다.",
                "/meeting/" + e.getRoomId() + "?tab=minutes", "MEETING", String.valueOf(e.getRoomId()), SYSTEM_ACTOR_ID,
                null, null
            )
        );
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
