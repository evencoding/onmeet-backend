package com.onmeet.ai.pipeline.stt;

import com.onmeet.ai.pipeline.storage.StorageClient;
import org.junit.jupiter.api.BeforeEach;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청크 분할 STT 통합 테스트
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest(classes = { OpenAiSttClient.class, ChunkedSttIntegrationTest.Config.class })
@Import(WebClientAutoConfiguration.class)
@DisplayName("Chunked STT Integration Test (Large File Processing)")
class ChunkedSttIntegrationTest {

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

    // 청크 파일 목록
    private static final List<String> CHUNK_FILES = Arrays.asList(
            "assembly_chunk_00_10min.m4a",
            "assembly_chunk_01_10min.m4a",
            "assembly_chunk_02_10min.m4a",
            "assembly_chunk_03_10min.m4a",
            "assembly_chunk_05_10min.m4a");

    // 결과 파일 저장 경로
    private static final String OUTPUT_DIR = "src/test/resources/stt_test";

    @BeforeEach
    void setup() {
        // No setup needed
    }

    @Test
    @DisplayName("Chunked Audio Files -> gpt-4o-mini-transcribe -> Individual & Merged Results")
    void testChunkedSttPipeline() throws IOException {
        List<String> allTranscripts = new ArrayList<>();
        Path outputPath = Paths.get(System.getProperty("user.dir"), OUTPUT_DIR);

        for (int i = 0; i < CHUNK_FILES.size(); i++) {
            String chunkFile = CHUNK_FILES.get(i);

            // 1. 파일 로드
            ClassPathResource resource = new ClassPathResource("stt_test/" + chunkFile);
            assertThat(resource.exists()).as("Chunk file must exist: " + chunkFile).isTrue();

            byte[] audioBytes = StreamUtils.copyToByteArray(resource.getInputStream());

            // 2. STT 변환
            String transcript = sttClient.transcribe(audioBytes, chunkFile, "audio/m4a");
            assertThat(transcript).isNotNull().isNotEmpty();
            allTranscripts.add(transcript);

            // 3. 개별 결과 파일 저장
            String resultFileName = chunkFile.replace(".m4a", "_result.txt");
            Path resultPath = outputPath.resolve(resultFileName);
            Files.writeString(resultPath, transcript, StandardCharsets.UTF_8);
        }

        // 4. 전체 병합 결과 저장
        String mergedPlainText = String.join("\n", allTranscripts);
        Path mergedPlainPath = outputPath.resolve("assembly_chunks_merged_plain.txt");
        Files.writeString(mergedPlainPath, mergedPlainText, StandardCharsets.UTF_8);

        assertThat(allTranscripts).hasSize(CHUNK_FILES.size());
        assertThat(mergedPlainText).isNotEmpty();
    }
}

