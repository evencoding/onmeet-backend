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

        String transcriptJson;
        if (e.getTranscriptFileId() != null) {
            transcriptJson = storageClient.readText(e.getTranscriptFileId());
        } else {
            transcriptJson = storageClient.readText(e.getTranscriptS3Key());
        }

        TranscriptDocument doc;
        try {
            doc = om.readValue(transcriptJson, TranscriptDocument.class);
        } catch (Exception ex) {
            throw new BusinessException(AiErrorCode.TRANSCRIPT_PARSE_FAILED);
        }

        String plain = renderer.toPlainText(doc);
        if (plain == null || plain.isBlank()) {
            throw new BusinessException(AiErrorCode.TRANSCRIPT_EMPTY);
        }

        String summaryJson = summarizerClient.summarize(plain, "ko", "default", null);

        // 요약 결과 파싱
        String description = null;
        String keywords = null;
        String decisions = null;
        String actionItems = null;

        try {
            com.onmeet.common.dto.ai.SummaryResult sr = om.readValue(summaryJson, com.onmeet.common.dto.ai.SummaryResult.class);
            description = sr.getDescription();
            if (sr.getKeywords() != null) keywords = om.writeValueAsString(sr.getKeywords());
            if (sr.getDecisions() != null) decisions = om.writeValueAsString(sr.getDecisions());
            if (sr.getActionItems() != null) actionItems = om.writeValueAsString(sr.getActionItems());
        } catch (Exception ignored) {
            // 파싱 실패 시 원본만 저장되도록 null 유지
            System.err.println("Failed to parse summaryJson in SummaryWorkerService: " + ignored.getMessage());
        }

        // 파일 서버에 요약본 업로드
        String summaryFilename = e.getTranscriptId() + "_summary.json";
        String summaryFileId = storageClient.writeText(summaryFilename, summaryJson, "application/json", "summary", "MEETING", String.valueOf(e.getRoomId()));

        upsertMinutes(
                e.getRoomId(),
                e.getTranscriptId(),
                e.getTranscriptFileId() != null ? String.valueOf(e.getTranscriptFileId()) : e.getTranscriptS3Key(),
                summaryFileId,
                description,
                keywords,
                decisions,
                actionItems,
                summaryJson
        );

        producer.publish(MinutesGeneratedEvent.builder()
                .roomId(e.getRoomId())
                .transcriptId(e.getTranscriptId())
                .transcriptS3Key(e.getTranscriptFileId() != null ? String.valueOf(e.getTranscriptFileId()) : e.getTranscriptS3Key())
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
            String description,
            String keywords,
            String decisions,
            String actionItems,
            String summaryJson
    ) {
        Minutes m = minutesRepository.findByRoomId(roomId).orElse(null);

        if (m == null) {
            minutesRepository.save(
                    Minutes.createGenerated(roomId, transcriptId, transcriptS3Key, summaryS3Key, description, keywords, decisions, actionItems, summaryJson)
            );
            return;
        }

        m.applyGenerated(transcriptId, transcriptS3Key, summaryS3Key, description, keywords, decisions, actionItems, summaryJson);
        minutesRepository.save(m);
    }
}
