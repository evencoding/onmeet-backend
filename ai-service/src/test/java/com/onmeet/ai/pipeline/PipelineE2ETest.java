package com.onmeet.ai.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.ChatMessageEvent;
import com.onmeet.ai.dto.event.TranscriptFinalizedEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
                "app.kafka.topics.minutes-generated=minutes.generated"
})
class PipelineE2ETest {

        // S3Client 빈 재정의 (LocalStack 용)
        @TestConfiguration
        static class LocalStackConfig {
                @Bean
                public S3Client s3Client() {
                        return S3Client.builder()
                                        .endpointOverride(URI.create("http://localhost:4566"))
                                        .forcePathStyle(true)
                                        .credentialsProvider(StaticCredentialsProvider.create(
                                                        AwsBasicCredentials.create("test", "test")))
                                        .region(Region.of("ap-northeast-2"))
                                        .build();
                }
        }

        @Autowired
        TranscriptBuilderService transcriptBuilderService;

        @Autowired
        StorageClient storageClient;

        @Autowired
        ObjectMapper om;

        @BeforeEach
        void setup() {
                // S3 버킷 생성 (LocalStack)
                try {
                        S3Client s3 = S3Client.builder()
                                        .endpointOverride(URI.create("http://localhost:4566"))
                                        .forcePathStyle(true)
                                        .credentialsProvider(StaticCredentialsProvider.create(
                                                        AwsBasicCredentials.create("test", "test")))
                                        .region(Region.of("ap-northeast-2"))
                                        .build();
                        s3.createBucket(b -> b.bucket("onmeet-transcripts"));
                } catch (Exception ignored) {
                        // 이미 존재하면 무시
                }
        }

        @Test
        @DisplayName("채팅과 음성 세그먼트가 입력되면 트랜스크립트가 생성되고 S3에 업로드되어야 한다")
        void shouldGenerateTranscriptAndUploadToS3() throws Exception {
                String meetingId = "meeting-e2e-test";

                // 1. Ingest Chat
                transcriptBuilderService.ingestChat(ChatMessageEvent.builder()
                                .meetingId(meetingId)
                                .messageId("msg-1")
                                .senderId("user-A")
                                .content("Hello E2E")
                                .atMs(100L)
                                .seq(1)
                                .occurredAtEpochMs(Instant.now().toEpochMilli())
                                .build());

                // 2. Ingest Voice
                transcriptBuilderService.ingestVoice(VoiceSegmentCreatedEvent.builder()
                                .meetingId(meetingId)
                                .segmentId("seg-1")
                                .participantId("user-B")
                                .text("This is voice")
                                .startMs(200L)
                                .endMs(500L)
                                .seq(2L)
                                .occurredAtEpochMs(Instant.now().toEpochMilli())
                                .build());

                // 3. Finalize Meeting
                transcriptBuilderService.finalizeMeeting(meetingId, Instant.now().toEpochMilli());

                // 4. Verify S3 Upload
                // key pattern: transcripts/{meetingId}/{transcriptId}.json but logic might
                // separate via StorageKeyFactory
                // We need to know the key. Since we don't have the event listener to catch the
                // finalized event easy here without custom consumer,
                // we can guess the prefix or list objects.

                S3Client s3 = S3Client.builder()
                                .endpointOverride(java.net.URI.create("http://localhost:4566"))
                                .forcePathStyle(true)
                                .credentialsProvider(StaticCredentialsProvider.create(
                                                AwsBasicCredentials.create("test", "test")))
                                .region(Region.of("ap-northeast-2"))
                                .build();

                await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                        var response = s3.listObjectsV2(
                                        b -> b.bucket("onmeet-transcripts").prefix("transcripts/" + meetingId));
                        assertThat(response.contents()).isNotEmpty();

                        String key = response.contents().get(0).key();
                        String json = storageClient.readText(key);
                        assertThat(json).contains("Hello E2E");
                        assertThat(json).contains("This is voice");
                });
        }
}
