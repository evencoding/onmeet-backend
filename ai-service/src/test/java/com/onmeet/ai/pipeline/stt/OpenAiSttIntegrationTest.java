package com.onmeet.ai.pipeline.stt;

import com.onmeet.ai.config.AwsS3Config;
import com.onmeet.ai.pipeline.storage.S3StorageClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAI STT + LocalStack S3 통합 테스트
 */
@ActiveProfiles("test")
@SpringBootTest(classes = {
        OpenAiSttClient.class,
        AwsS3Config.class,
        S3StorageClient.class
})
@Import(WebClientAutoConfiguration.class)
@DisplayName("OpenAI STT Integration Test (Real API & LocalStack)")
class OpenAiSttIntegrationTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class Config {
        @org.springframework.context.annotation.Bean
        public org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder() {
            return org.springframework.web.reactive.function.client.WebClient.builder();
        }
    }

    @Autowired
    private OpenAiSttClient sttClient;

    @Autowired
    private S3StorageClient storageClient;

    @Autowired
    private S3Client s3Client;

    private final String bucketName = "onmeet-transcripts";

    @BeforeEach
    void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    @Test
    @DisplayName("Audio File -> OpenAI STT -> LocalStack S3 Integration Flow")
    void testFullIntegration() throws IOException {
        // 1. Load Audio File
        ClassPathResource resource = new ClassPathResource("stt_test/audio_Test_5.m4a");
        assertThat(resource.exists()).as("Test audio file must exist").isTrue();

        byte[] audioBytes = StreamUtils.copyToByteArray(resource.getInputStream());

        // 2. Transcribe (Real Call to OpenAI)
        String transcript = sttClient.transcribe(audioBytes, "audio_Test_5.m4a", "audio/m4a");
        assertThat(transcript).isNotNull().isNotEmpty();
        System.out.println("Transcript Result: " + transcript);

        // 3. Save to S3 (LocalStack)
        String s3Key = "integration-test/transcript-result.txt";
        storageClient.writeText(s3Key, transcript, "text/plain");

        // 4. Verify in S3
        String savedContent = storageClient.readText(s3Key);
        assertThat(savedContent).isEqualTo(transcript);

        // Cleanup
        storageClient.delete(s3Key);
    }
}
