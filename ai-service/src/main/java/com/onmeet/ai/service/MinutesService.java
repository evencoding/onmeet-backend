package com.onmeet.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.request.MinutesPatchRequest;
import com.onmeet.ai.dto.request.MinutesRegenerateRequest;
import com.onmeet.ai.dto.response.MinutesResponse;
import com.onmeet.ai.entity.Minutes;
import com.onmeet.ai.pipeline.nlp.SummarizerClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.storage.StorageKeyFactory;
import com.onmeet.ai.pipeline.transcript.TranscriptDocument;
import com.onmeet.ai.pipeline.transcript.TranscriptRenderer;
import com.onmeet.ai.repository.MinutesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MinutesService {

    private final MinutesRepository minutesRepository;
    private final StorageClient storageClient;
    private final ObjectMapper om;
    private final TranscriptRenderer renderer;
    private final SummarizerClient summarizerClient;

    public MinutesService(
            MinutesRepository minutesRepository,
            StorageClient storageClient,
            ObjectMapper om,
            TranscriptRenderer renderer,
            SummarizerClient summarizerClient
    ) {
        this.minutesRepository = minutesRepository;
        this.storageClient = storageClient;
        this.om = om;
        this.renderer = renderer;
        this.summarizerClient = summarizerClient;
    }

    @Transactional(readOnly = true)
    public MinutesResponse get(String meetingId) {
        Minutes m = minutesRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("minutes not found: " + meetingId));
        return MinutesResponse.from(m);
    }

    /**
     * ✅ 동기 재요약 (테스트 우선)
     * - minutes에 저장된 transcriptS3Key로 transcript 로드
     * - 요약 생성
     * - summary는 S3 + DB에 반영
     */
    @Transactional
    public MinutesResponse regenerate(String meetingId, MinutesRegenerateRequest req) {
        Minutes m = minutesRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("minutes not found: " + meetingId));

        try {
            String transcriptJson = storageClient.readText(m.getTranscriptS3Key());
            TranscriptDocument doc = om.readValue(transcriptJson, TranscriptDocument.class);

            String plain = renderer.toPlainText(doc);
            if (plain == null || plain.isBlank()) {
                throw new IllegalStateException("empty transcript");
            }

            String language = (req != null && req.getLanguage() != null) ? req.getLanguage() : "ko";
            String style = (req != null && req.getStyle() != null) ? req.getStyle() : "default";
            String model = (req != null && req.getModel() != null) ? req.getModel() : "claude-sonnet";

            String summaryJson = summarizerClient.summarize(plain, language, style, model);

            // ✅ transcriptId 기준으로 summaryKey 생성( Job 없음 )
            String summaryKey = StorageKeyFactory.summaryKey(meetingId, m.getTranscriptId());
            storageClient.writeText(summaryKey, summaryJson, "application/json");

            m.applyGenerated(m.getTranscriptId(), m.getTranscriptS3Key(), summaryKey, summaryJson);
            minutesRepository.save(m);

            return MinutesResponse.from(m);

        } catch (Exception e) {
            m.markFailed(e.getMessage());
            minutesRepository.save(m);
            throw new IllegalStateException("regenerate failed: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ 수정(공개범위/사용자 편집본)
     */
    @Transactional
    public MinutesResponse patch(String meetingId, MinutesPatchRequest req) {
        Minutes m = minutesRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("minutes not found: " + meetingId));

        if (req.getAccessScope() != null) {
            m.updateAccessScope(req.getAccessScope());
        }
        if (req.getUserEditedSummaryJson() != null) {
            m.applyUserEdit(req.getUserEditedSummaryJson());
        }

        minutesRepository.save(m);
        return MinutesResponse.from(m);
    }

    /**
     * 선택: transcript 원문(JSON) 확인용
     */
    @Transactional(readOnly = true)
    public String getTranscriptRawJson(String meetingId) {
        Minutes m = minutesRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("minutes not found: " + meetingId));
        return storageClient.readText(m.getTranscriptS3Key());
    }
}
