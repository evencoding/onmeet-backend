package com.onmeet.ai.pipeline.nlp;

import com.onmeet.ai.pipeline.nlp.ClaudeSummarizerClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Claude 요약 기능 통합 테스트
 *
 * STT로 생성된 텍스트를 Claude에 보내 요약이 정상적으로 되는지 검증합니다.
 * - 개별 청크 요약 테스트
 * - 병합 텍스트 전체 요약 테스트
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest(classes = { ClaudeSummarizerClient.class })
@DisplayName("Claude Summarizer Integration Test (Claude API)")
class ClaudeSummarizerIntegrationTest {

    @MockBean
    private StorageClient storageClient;

    @org.springframework.boot.test.context.TestConfiguration
    static class Config {
        @org.springframework.context.annotation.Bean
        public org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder() {
            return org.springframework.web.reactive.function.client.WebClient.builder();
        }

        @org.springframework.context.annotation.Bean
        public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
    }

    @Autowired
    private ClaudeSummarizerClient summarizerClient;

    @Test
    @DisplayName("병합 텍스트 전체 요약 테스트")
    void testSummarizeMergedTranscript() throws IOException {
        // 1. 병합된 STT 결과 파일 로드
        ClassPathResource resource = new ClassPathResource("stt_test/assembly_chunks_merged_plain.txt");
        assertThat(resource.exists()).as("Merged transcript file must exist").isTrue();

        String transcript = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        assertThat(transcript).isNotEmpty();
        System.out.println("=== Input transcript length: " + transcript.length() + " chars ===");

        // 2. Claude 요약 호출
        long startTime = System.currentTimeMillis();
        String summary = summarizerClient.summarize(
                transcript,
                "Korean", // 한국어로 요약
                "formal", // 공식 스타일
                null // 기본 모델 사용 (claude-sonnet-4-20250514)
        );
        long elapsed = System.currentTimeMillis() - startTime;

        // 3. 검증
        assertThat(summary).isNotNull().isNotEmpty();
        System.out.println("=== Summarization Result ===");
        System.out.println("Time: " + elapsed + "ms");
        System.out.println("Summary length: " + summary.length() + " chars");
        System.out.println("---");
        System.out.println(summary);
        System.out.println("---");

        // 4. 결과 파일 저장
        Path outputPath = Paths.get(System.getProperty("user.dir"),
                "src/test/resources/nlp_test/assembly_chunks_summary_result.txt");
        Files.writeString(outputPath, summary, StandardCharsets.UTF_8);
        System.out.println("Saved: " + outputPath.getFileName());
    }

    @Test
    @DisplayName("개별 청크 요약 테스트 (첫 번째 청크)")
    void testSummarizeSingleChunk() throws IOException {
        // 1. 첫 번째 청크 결과 로드
        ClassPathResource resource = new ClassPathResource("stt_test/assembly_chunks_merged_plain.txt");
        if (!resource.exists()) {
            System.out.println(
                    "SKIP: assembly_chunks_merged_plain.txt not found. Run ChunkedSttIntegrationTest first.");
            return;
        }

        String transcript = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        assertThat(transcript).isNotEmpty();
        System.out.println("=== Chunk 0 transcript length: " + transcript.length() + " chars ===");

        // 2. Claude 요약 호출
        long startTime = System.currentTimeMillis();
        String summary = summarizerClient.summarize(
                transcript,
                "Korean",
                "bullet-point", // 불릿 포인트 스타일
                null);
        long elapsed = System.currentTimeMillis() - startTime;

        // 3. 검증
        assertThat(summary).isNotNull().isNotEmpty();
        System.out.println("=== Chunk 0 Summary ===");
        System.out.println("Time: " + elapsed + "ms");
        System.out.println("---");
        System.out.println(summary);
        System.out.println("---");

        // 4. 결과 파일 저장
        Path outputPath = Paths.get(System.getProperty("user.dir"),
                "src/test/resources/nlp_test/assembly_chunks_summary_bullet_result.txt");
        Files.writeString(outputPath, summary, StandardCharsets.UTF_8);
        System.out.println("Saved: " + outputPath.getFileName());
    }
}
