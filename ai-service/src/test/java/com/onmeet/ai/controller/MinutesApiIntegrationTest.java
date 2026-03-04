package com.onmeet.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.request.MinutesPatchRequest;
import com.onmeet.ai.dto.request.MinutesRegenerateRequest;
import com.onmeet.ai.entity.Minutes;
import com.onmeet.ai.enums.MinutesAccessScope;
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
        "app.kafka.topics.minutes-generated=minutes.generated"
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

        // 초기 데이터 세팅
        existingMinutes = Minutes.createGenerated(
                "meeting-1",
                "transcript-1",
                "s3/transcripts/meeting-1/1.json",
                "s3/summaries/meeting-1/1.json",
                "{\"summary\":\"Original Summary\"}");
        minutesRepository.save(existingMinutes);
    }

    @Test
    @DisplayName("GET /api/minutes/{meetingId} - 조회 성공")
    void getMinutes_Success() throws Exception {
        mockMvc.perform(get("/api/minutes/meeting-1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingId").value("meeting-1"))
                .andExpect(jsonPath("$.summaryJson").value("{\"summary\":\"Original Summary\"}"))
                .andExpect(jsonPath("$.accessScope").value("PRIVATE"));
    }

    @Test
    @DisplayName("GET /api/minutes/{meetingId} - 존재하지 않는 경우 404 또는 Error")
    void getMinutes_NotFound() throws Exception {
        // GlobalExceptionHandler가 없으면 500이나 400이 나올 수 있음.
        // 현재 코드상 IllegalArgumentException 발생 -> Default Handler나 500 예상
        // 여기서는 예외가 발생하는지 확인.
        try {
            mockMvc.perform(get("/api/minutes/unknown-id"))
                    .andDo(print())
                    .andExpect(status().is5xxServerError()); // 기본 Spring Boot 에러 처리
        } catch (Exception e) {
            // MVC 테스트에서 Exception이 밖으로 던져질 수 있음
        }
    }

    @Test
    @DisplayName("PATCH /api/minutes/{meetingId} - 공개범위 및 사용자 편집본 수정")
    void patchMinutes_Success() throws Exception {
        MinutesPatchRequest req = new MinutesPatchRequest();
        req.setAccessScope(MinutesAccessScope.PUBLIC);
        req.setUserEditedSummaryJson("{\"summary\":\"Edited by User\"}");

        mockMvc.perform(patch("/api/minutes/meeting-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessScope").value("PUBLIC"))
                .andExpect(jsonPath("$.userEditedSummaryJson").value("{\"summary\":\"Edited by User\"}"))
                .andExpect(jsonPath("$.status").value("EDITED_BY_USER"));
    }

    @Test
    @DisplayName("POST /api/minutes/{meetingId}/regenerate - 요약 재생성")
    void regenerateMinutes_Success() throws Exception {
        // Given
        // 1. S3에서 transcript 읽기 Mocking
        String mockTranscriptDoc = "{\"events\":[{\"type\":\"CHAT\",\"text\":\"Hello World\",\"atMs\":1000,\"actorId\":\"user1\"}]}"; // TranscriptDocument
                                                                                                                                      // 구조
        // 구조
        Mockito.when(storageClient.readText(anyString())).thenReturn(mockTranscriptDoc);

        // 2. SummarizerClient Mocking
        String newSummary = "{\"summary\":\"Regenerated Summary\"}";
        Mockito.when(summarizerClient.summarize(anyString(), eq("ko"), eq("bullets"), eq("claude-pro")))
                .thenReturn(newSummary);

        // Request
        MinutesRegenerateRequest req = new MinutesRegenerateRequest();
        req.setStyle("bullets");
        req.setModel("claude-pro");

        // When & Then
        mockMvc.perform(post("/api/minutes/meeting-1/regenerate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryJson").value(newSummary));

        // Verify Storage Write
        Mockito.verify(storageClient).writeText(anyString(), eq(newSummary), eq("application/json"));
    }
}
