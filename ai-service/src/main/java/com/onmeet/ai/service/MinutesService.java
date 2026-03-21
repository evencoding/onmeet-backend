package com.onmeet.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.request.MinutesPatchRequest;
import com.onmeet.ai.dto.request.MinutesRegenerateRequest;
import com.onmeet.ai.dto.response.MinutesResponse;
import com.onmeet.ai.dto.response.TranscriptResponse;
import com.onmeet.ai.entity.Minutes;
import com.onmeet.ai.pipeline.nlp.SummarizerClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.storage.StorageKeyFactory;
import com.onmeet.ai.pipeline.transcript.TranscriptDocument;
import com.onmeet.ai.pipeline.transcript.TranscriptRenderer;
import com.onmeet.ai.repository.MinutesRepository;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.AiErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
    public MinutesResponse get(Long roomId) {
        Minutes m = findMinutesOrThrow(roomId);
        return MinutesResponse.from(m);
    }

    @Transactional(readOnly = true)
    public java.util.List<MinutesResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return minutesRepository.searchByKeyword(keyword).stream()
                .map(MinutesResponse::from)
                .toList();
    }

    @Transactional
    public MinutesResponse regenerate(Long roomId, MinutesRegenerateRequest req) {
        Minutes m = findMinutesOrThrow(roomId);

        try {
            String transcriptJson = storageClient.readText(m.getTranscriptS3Key());
            TranscriptDocument doc;
            try {
                doc = om.readValue(transcriptJson, TranscriptDocument.class);
            } catch (Exception e) {
                // TODO: [AI][AiErrorCode.TRANSCRIPT_PARSE_FAILED] 에러메시지 검수 요청
                throw new BusinessException(AiErrorCode.TRANSCRIPT_PARSE_FAILED);
            }

            String plain = renderer.toPlainText(doc);
            if (plain == null || plain.isBlank()) {
                // TODO: [AI][AiErrorCode.TRANSCRIPT_EMPTY] 에러메시지 검수 요청
                throw new BusinessException(AiErrorCode.TRANSCRIPT_EMPTY);
            }

            String language = (req != null && req.getLanguage() != null) ? req.getLanguage() : "ko";
            String style = (req != null && req.getStyle() != null) ? req.getStyle() : "default";
            String model = (req != null && req.getModel() != null) ? req.getModel() : "claude-sonnet";

            String summaryJson = summarizerClient.summarize(plain, language, style, model);

            String summaryKey = StorageKeyFactory.summaryKey(roomId, m.getTranscriptId());
            storageClient.writeText(summaryKey, summaryJson, "application/json");

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
                System.err.println("Failed to parse summaryJson in MinutesService: " + ignored.getMessage());
            }

            m.applyGenerated(m.getTranscriptId(), m.getTranscriptS3Key(), summaryKey, description, keywords, decisions, actionItems, summaryJson);
            minutesRepository.save(m);

            return MinutesResponse.from(m);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            m.markFailed(e.getMessage());
            minutesRepository.save(m);
            // TODO: [AI][AiErrorCode.SUMMARIZE_FAILED] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.SUMMARIZE_FAILED);
        }
    }

    @Transactional
    public MinutesResponse patch(Long roomId, MinutesPatchRequest req) {
        Minutes m = findMinutesOrThrow(roomId);

        if (req.getUserEditedSummaryJson() != null) {
            String description = null;
            String keywords = null;
            String decisions = null;
            String actionItems = null;

            try {
                com.onmeet.common.dto.ai.SummaryResult sr = 
                        om.readValue(req.getUserEditedSummaryJson(), com.onmeet.common.dto.ai.SummaryResult.class);
                description = sr.getDescription();
                if (sr.getKeywords() != null) keywords = om.writeValueAsString(sr.getKeywords());
                if (sr.getDecisions() != null) decisions = om.writeValueAsString(sr.getDecisions());
                if (sr.getActionItems() != null) actionItems = om.writeValueAsString(sr.getActionItems());
            } catch (Exception ignored) {
                System.err.println("Failed to parse userEditedSummaryJson in MinutesService patch: " + ignored.getMessage());
            }

            m.applyUserEdit(req.getUserEditedSummaryJson(), description, keywords, decisions, actionItems);
        }

        minutesRepository.save(m);
        return MinutesResponse.from(m);
    }

    @Transactional(readOnly = true)
    public TranscriptResponse getTranscript(Long roomId) {
        Minutes m = findMinutesOrThrow(roomId);
        String transcript = storageClient.readText(m.getTranscriptS3Key());
        LocalDateTime createdAt = LocalDateTime.ofInstant(m.getCreatedAt(), ZoneOffset.UTC);
        return new TranscriptResponse(roomId, transcript, createdAt);
    }

    @Transactional
    public void delete(Long roomId) {
        minutesRepository.findByRoomId(roomId).ifPresent(m -> {
            try {
                if (m.getTranscriptS3Key() != null) {
                    storageClient.delete(m.getTranscriptS3Key());
                }
                if (m.getSummaryS3Key() != null) {
                    storageClient.delete(m.getSummaryS3Key());
                }
            } catch (Exception e) {
                // S3 삭제 실패 시 무시하고 데이터베이스 레코드는 계속 지우도록 처리
                System.err.println("Failed to delete S3 objects for minutes: " + e.getMessage());
            }
            minutesRepository.delete(m);
        });
    }

    private Minutes findMinutesOrThrow(Long roomId) {
        return minutesRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessException(AiErrorCode.MINUTES_NOT_FOUND));
    }
}
