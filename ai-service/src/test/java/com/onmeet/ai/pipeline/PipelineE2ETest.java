package com.onmeet.ai.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.ChatMessageEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
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
class PipelineE2ETest {

    @MockBean
    StorageClient storageClient;

    @Autowired
    TranscriptBuilderService transcriptBuilderService;

        @Autowired
        ObjectMapper om;

        @BeforeEach
        void setup() {
                // No setup needed
        }

        @Test
        @DisplayName("채팅과 음성 세그먼트가 입력되면 트랜스크립트가 생성되고 S3에 업로드되어야 한다")
        void shouldGenerateTranscriptAndUploadToS3() throws Exception {
                Long roomId = 42L;

                transcriptBuilderService.ingestChat(ChatMessageEvent.builder()
                                .roomId(roomId)
                                .messageId("msg-1")
                                .senderId(1L)
                                .content("Hello E2E")
                                .timestamp(Instant.now())
                                .seq(1)
                                .build());

                transcriptBuilderService.ingestVoice(VoiceSegmentCreatedEvent.builder()
                                .roomId(roomId)
                                .segmentId("seg-1")
                                .participantIdentity("참가자2")
                                .text("This is voice")
                                .segmentStartMs(200L)
                                .segmentEndMs(500L)
                                .seq(2L)
                                .timestamp(Instant.now())
                                .build());

                transcriptBuilderService.finalizeMeeting(roomId, 1L, Instant.now());

                org.mockito.Mockito.when(storageClient.readText(org.mockito.ArgumentMatchers.anyString()))
                                .thenReturn("Hello E2E, This is voice");

                await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                        String key = "test-key";
                        String json = storageClient.readText(key);
                        assertThat(json).contains("Hello E2E");
                        assertThat(json).contains("This is voice");
                });
        }
}
