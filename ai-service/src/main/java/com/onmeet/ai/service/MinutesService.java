package com.onmeet.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.request.MinutesPatchRequest;
import com.onmeet.ai.dto.request.MinutesRegenerateRequest;
import com.onmeet.ai.dto.response.MinutesResponse;
import com.onmeet.ai.dto.response.TranscriptResponse;
import com.onmeet.ai.entity.Minutes;
import com.onmeet.ai.entity.TranscriptEvent;
import com.onmeet.ai.pipeline.nlp.SummarizerClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.storage.StorageKeyFactory;
import com.onmeet.ai.pipeline.transcript.TranscriptAssembler;
import com.onmeet.ai.pipeline.transcript.TranscriptRenderer;
import com.onmeet.ai.repository.MinutesRepository;
import com.onmeet.ai.repository.TranscriptEventRepository;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.AiErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class MinutesService {

    private final MinutesRepository minutesRepository;
    private final TranscriptEventRepository transcriptEventRepository;
    private final StorageClient storageClient;
    private final ObjectMapper om;
    private final TranscriptRenderer renderer;
    private final TranscriptAssembler assembler;
    private final SummarizerClient summarizerClient;

    public MinutesService(
            MinutesRepository minutesRepository,
            TranscriptEventRepository transcriptEventRepository,
            StorageClient storageClient,
            ObjectMapper om,
            TranscriptRenderer renderer,
            TranscriptAssembler assembler,
            SummarizerClient summarizerClient
    ) {
        this.minutesRepository = minutesRepository;
        this.transcriptEventRepository = transcriptEventRepository;
        this.storageClient = storageClient;
        this.om = om;
        this.renderer = renderer;
        this.assembler = assembler;
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
            // DB의 transcript_event 테이블에서 직접 조회
            java.util.List<TranscriptEvent> events =
                    transcriptEventRepository.findAllByTranscriptIdOrderBySeqAsc(m.getTranscriptId());
            
            String plain;
            if (events.isEmpty()) {
                // DB에 없을 경우 하위 호환성 (과거 S3 JSON 파일) 
                com.onmeet.ai.pipeline.transcript.TranscriptDocument doc;
                try {
                    // fileId 숫자인지 확인
                    Long fileId = Long.parseLong(m.getTranscriptId());
                    doc = assembler.load(fileId);
                } catch (NumberFormatException nfe) {
                    doc = assembler.load(m.getTranscriptId());
                }
                plain = assembler.assemblePlainText(doc);
            } else {
                plain = renderer.toPlainText(events);
            }
            
            if (plain == null || plain.isBlank()) {
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

            m.applyGenerated(m.getTranscriptId(), summaryKey, description, keywords, decisions, actionItems, summaryJson);
            minutesRepository.save(m);

            return MinutesResponse.from(m);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            m.markFailed(e.getMessage());
            minutesRepository.save(m);
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
        // DB에서 TranscriptEvent 목록을 조회
        java.util.List<TranscriptEvent> events =
                transcriptEventRepository.findAllByTranscriptIdOrderBySeqAsc(m.getTranscriptId());
        
        String plainText;
        if (events.isEmpty()) {
            // 하위 호환 지원: S3에서 다운로드
            com.onmeet.ai.pipeline.transcript.TranscriptDocument doc;
            try {
                Long fileId = Long.parseLong(m.getTranscriptId());
                doc = assembler.load(fileId);
            } catch (NumberFormatException nfe) {
                doc = assembler.load(m.getTranscriptId());
            }
            plainText = assembler.assemblePlainText(doc);
        } else {
            plainText = renderer.toPlainText(events);
        }
        
        java.time.LocalDateTime createdAt = java.time.LocalDateTime.ofInstant(m.getCreatedAt(), java.time.ZoneOffset.UTC);
        return new TranscriptResponse(roomId, plainText, createdAt);
    }

    @Transactional
    public void delete(Long roomId) {
        minutesRepository.findByRoomId(roomId).ifPresent(m -> {
            // summary S3 파일만 삭제 (transcript은 DB transcript_event 테이블에서 cascade 삭제됨)
            try {
                if (m.getSummaryS3Key() != null) {
                    storageClient.delete(m.getSummaryS3Key());
                }
            } catch (Exception e) {
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
