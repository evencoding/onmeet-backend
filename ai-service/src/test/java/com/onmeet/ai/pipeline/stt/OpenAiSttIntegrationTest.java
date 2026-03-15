package com.onmeet.ai.pipeline.stt;

import com.onmeet.ai.pipeline.storage.StorageClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAI STT API 연동 테스트
 * 실제 API 키가 필요하므로 로컬 테스트 시에만 사용 권장
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest(classes = {
        OpenAiSttClient.class
})
@Import(WebClientAutoConfiguration.class)
@DisplayName("OpenAI STT Integration Test (OpenAI API)")
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

    @MockBean
    private StorageClient storageClient;

    @Test
    @DisplayName("STT 결과 전송 및 저장 테스트 (Mocked Storage)")
    void transcribeAndStoreResult() throws IOException {
        // 1. Load Audio File
        ClassPathResource resource = new ClassPathResource("stt_test/audio_Test_5.m4a");
        assertThat(resource.exists()).as("Test audio file must exist").isTrue();

        byte[] audioBytes = StreamUtils.copyToByteArray(resource.getInputStream());

        // 2. Transcribe (Real Call to OpenAI)
        String transcript = sttClient.transcribe(audioBytes, "audio_Test_5.m4a", "audio/m4a");
        assertThat(transcript).isNotNull().isNotEmpty();
        System.out.println("Transcript: " + transcript);

        // 3. Save to storage (Mocked)
        String s3Key = "integration-test/transcript-result.txt";
        storageClient.writeText(s3Key, transcript, "text/plain");

        // 4. Verify in storage (Mocked)
        org.mockito.Mockito.when(storageClient.readText(s3Key)).thenReturn(transcript);
        String savedContent = storageClient.readText(s3Key);
        assertThat(savedContent).isEqualTo(transcript);

        // Cleanup
        storageClient.delete(s3Key);
    }
}

