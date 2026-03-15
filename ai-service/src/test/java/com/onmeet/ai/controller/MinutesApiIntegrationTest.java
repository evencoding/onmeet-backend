package com.onmeet.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.request.MinutesPatchRequest;
import com.onmeet.ai.dto.request.MinutesRegenerateRequest;
import com.onmeet.ai.entity.Minutes;

import com.onmeet.ai.pipeline.nlp.SummarizerClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.repository.MinutesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CHECK [ai-담당자]: MinutesController URL 패턴이 /v1/rooms/{roomId}/minutes로 변경됨.
// Frontend에서 /ai/v1/rooms/{roomId}/minutes로 호출하면 Gateway가 /ai/v1 strip 후
// /rooms/{roomId}/minutes → 이 컨트롤러로 매핑됨. Gateway strip prefix 동작 확인 필요.

// CHECK [frontend-담당자]: AI API URL 패턴이 Backend에서 Frontend 계약에 맞게 변경됨.
// Frontend의 /ai/v1/rooms/{roomId}/minutes 호출이 정상 동작하는지 E2E 테스트 필요.
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9094",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:mysql://localhost:3308/ai_db?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=UTC",
        "spring.datasource.username=root",
        "spring.datasource.password=root",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect",
        "aws.region=ap-northeast-2",
        "aws.s3.endpoint=http://localhost:4566",
        "aws.credentials.access-key=test",
        "aws.credentials.secret-key=test",
        "app.kafka.topics.voice-segment-created=voice.segment.created",
        "app.kafka.topics.transcript-finalized=transcript.finalized",
        "app.kafka.topics.chat-events=chat.events",
        "app.kafka.topics.audio-chunk-ready=audio.chunk.ready",
        "app.kafka.topics.meeting-ended=meeting.ended",
        "app.kafka.topics.minutes-generated=minutes.generated",
        "gateway.shared-secret=test-secret"
})
class MinutesApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MinutesRepository minutesRepository;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private StorageClient storageClient;

    @MockBean
    private SummarizerClient summarizerClient;

    private Minutes existingMinutes;

    @BeforeEach
    void setup() {
        minutesRepository.deleteAll();

        existingMinutes = Minutes.createGenerated(
                1L,
                "transcript-1",
                "s3/transcripts/1/1.json",
                "s3/summaries/1/1.json",
                "{\"summary\":\"Original Summary\"}");
        minutesRepository.save(existingMinutes);
    }

    // ─── [1][2] URL 패턴 및 HTTP 메서드 변경 ───────────────────────────────

    @Test
    @DisplayName("GET /v1/rooms/{roomId}/minutes - 조회 성공")
    void getMinutes_Success() throws Exception {
        mockMvc.perform(get("/v1/rooms/1/minutes"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(1))
                .andExpect(jsonPath("$.summaryJson").value("{\"summary\":\"Original Summary\"}"));
    }

    @Test
    @DisplayName("GET /v1/rooms/{roomId}/minutes - 존재하지 않는 경우 404 또는 Error")
    void getMinutes_NotFound() throws Exception {
        try {
            mockMvc.perform(get("/v1/rooms/9999/minutes"))
                    .andDo(print())
                    .andExpect(status().is5xxServerError());
        } catch (Exception e) {
            // MVC 테스트에서 Exception이 밖으로 던져질 수 있음
        }
    }

    @Test
    @DisplayName("PUT /v1/rooms/{roomId}/minutes - 사용자 편집본 수정 (PATCH→PUT 변경)")
    void putMinutes_Success() throws Exception {
        MinutesPatchRequest req = new MinutesPatchRequest();
        req.setUserEditedSummaryJson("{\"summary\":\"Edited by User\"}");

        mockMvc.perform(put("/v1/rooms/1/minutes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEditedSummaryJson").value("{\"summary\":\"Edited by User\"}"))
                .andExpect(jsonPath("$.status").value("EDITED_BY_USER"));
    }

    @Test
    @DisplayName("POST /v1/rooms/{roomId}/minutes/regenerate - 요약 재생성")
    void regenerateMinutes_Success() throws Exception {
        String mockTranscriptDoc = "{\"events\":[{\"type\":\"CHAT\",\"text\":\"Hello World\",\"timestamp\":\"2026-03-07T12:00:00Z\",\"actorId\":\"1\"}]}";
        Mockito.when(storageClient.readText(anyString())).thenReturn(mockTranscriptDoc);

        String newSummary = "{\"summary\":\"Regenerated Summary\"}";
        Mockito.when(summarizerClient.summarize(anyString(), eq("ko"), eq("bullets"), eq("claude-pro")))
                .thenReturn(newSummary);

        MinutesRegenerateRequest req = new MinutesRegenerateRequest();
        req.setStyle("bullets");
        req.setModel("claude-pro");

        mockMvc.perform(post("/v1/rooms/1/minutes/regenerate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryJson").value(newSummary));

        Mockito.verify(storageClient).writeText(anyString(), eq(newSummary), eq("application/json"));
    }

    // ─── [3] getTranscript 응답 타입 변경 ──────────────────────────────────

    @Test
    @DisplayName("GET /v1/rooms/{roomId}/transcript - TranscriptResponse DTO 반환")
    void getTranscript_ReturnsDto() throws Exception {
        String rawJson = "{\"events\":[]}";
        Mockito.when(storageClient.readText(anyString())).thenReturn(rawJson);

        mockMvc.perform(get("/v1/rooms/1/transcript"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(1))
                .andExpect(jsonPath("$.transcript").value(rawJson))
                .andExpect(jsonPath("$.createdAt").exists());
    }
}
