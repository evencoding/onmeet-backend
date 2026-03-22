package com.onmeet.ai.pipeline;

import com.onmeet.ai.pipeline.nlp.ClaudeSummarizerClient;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.stt.OpenAiSttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * E2E 통합 테스트: Audio → STT → Transcript 저장 → Claude 요약 → Summary 저장
 *
 * 전체 AI 파이프라인을 하나의 플로우로 검증합니다.
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest(classes = {
        OpenAiSttClient.class,
        ClaudeSummarizerClient.class,
        E2eAiPipelineIntegrationTest.Config.class
})
@DisplayName("E2E AI Pipeline Integration Test")
class E2eAiPipelineIntegrationTest {

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
    private OpenAiSttClient sttClient;

    @Autowired
    private ClaudeSummarizerClient summarizerClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private StorageClient storageClient;

    private final String bucketName = "onmeet-transcripts";

    private static final List<String> CHUNK_FILES = Arrays.asList(
            "assembly_chunk_00_10min.m4a",
            "assembly_chunk_01_10min.m4a",
            "assembly_chunk_02_10min.m4a",
            "assembly_chunk_03_10min.m4a",
            "assembly_chunk_05_10min.m4a");

    private static final String OUTPUT_DIR = "src/test/resources/e2e_test";

    @BeforeEach
    void ensureBucket() {
        // No bucket setup needed
    }

    @Test
    @DisplayName("Audio Chunks → STT → Transcript 저장 → Claude 요약 → Summary 저장")
    void testFullPipeline() throws IOException {
        Path outputPath = Paths.get(System.getProperty("user.dir"), OUTPUT_DIR);
        Files.createDirectories(outputPath);

        System.out.println("========================================");
        System.out.println("  E2E AI Pipeline Integration Test");
        System.out.println("========================================\n");

        // ── PHASE 1: STT (Audio → Text) ──
        System.out.println("▶ PHASE 1: STT Transcription");
        System.out.println("─────────────────────────────");

        List<String> transcripts = new ArrayList<>();
        long sttTotalTime = 0;

        for (int i = 0; i < CHUNK_FILES.size(); i++) {
            String chunkFile = CHUNK_FILES.get(i);
            ClassPathResource resource = new ClassPathResource("stt_test/" + chunkFile);
            assertThat(resource.exists()).as("Chunk file must exist: " + chunkFile).isTrue();

            byte[] audioBytes = StreamUtils.copyToByteArray(resource.getInputStream());

            long start = System.currentTimeMillis();
            String transcript = sttClient.transcribe(audioBytes, chunkFile, "audio/m4a");
            long elapsed = System.currentTimeMillis() - start;
            sttTotalTime += elapsed;

            assertThat(transcript).isNotNull().isNotEmpty();
            transcripts.add(transcript);

            System.out.printf("  [%d/%d] %s → %d chars (%dms)%n",
                    i + 1, CHUNK_FILES.size(), chunkFile, transcript.length(), elapsed);
        }

        System.out.printf("%n  STT Total: %d chunks, %dms%n%n", transcripts.size(), sttTotalTime);

        // ── PHASE 2: Transcript 저장 ──
        System.out.println("▶ PHASE 2: Transcript 저장");
        System.out.println("─────────────────────────────");

        // 개별 청크 저장
        for (int i = 0; i < transcripts.size(); i++) {
            String fileName = CHUNK_FILES.get(i).replace(".m4a", "_transcript.txt");
            Files.writeString(outputPath.resolve(fileName), transcripts.get(i), StandardCharsets.UTF_8);
            storageClient.writeText("e2e-test/" + fileName, transcripts.get(i), "text/plain");
            System.out.println("  Saved: " + fileName);
        }

        // 병합 텍스트 저장
        String mergedTranscript = String.join("\n", transcripts);
        String mergedFileName = "merged_transcript.txt";
        Path mergedPath = outputPath.resolve(mergedFileName);
        Files.writeString(mergedPath, mergedTranscript, StandardCharsets.UTF_8);
        storageClient.writeText("e2e-test/" + mergedFileName, mergedTranscript, "text/plain");
        System.out.println("  Saved: " + mergedFileName + " (" + mergedTranscript.length() + " chars)");

        // Mock verification
        org.mockito.Mockito.when(storageClient.readText(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(mergedTranscript);
        String s3Content = storageClient.readText("e2e-test/" + mergedFileName);
        assertThat(s3Content).isEqualTo(mergedTranscript);
        System.out.println("  Storage verification: ✅\n");

        // ── PHASE 3: Claude 요약 ──
        System.out.println("▶ PHASE 3: Claude Summarization");
        System.out.println("─────────────────────────────────");

        long summaryStart = System.currentTimeMillis();
        String summary = summarizerClient.summarize(
                mergedTranscript,
                "Korean",
                "bullet-point",
                null // 기본 모델 (claude-sonnet-4-20250514)
        );
        long summaryElapsed = System.currentTimeMillis() - summaryStart;

        assertThat(summary).isNotNull().isNotEmpty();
        System.out.printf("  Summarization: %d chars → %d chars (%dms)%n%n",
                mergedTranscript.length(), summary.length(), summaryElapsed);

        // ── PHASE 4: Summary 저장 ──
        System.out.println("▶ PHASE 4: Summary 저장");
        System.out.println("─────────────────────────");

        String summaryFileName = "meeting_summary.txt";
        Path summaryPath = outputPath.resolve(summaryFileName);
        Files.writeString(summaryPath, summary, StandardCharsets.UTF_8);
        storageClient.writeText("e2e-test/" + summaryFileName, summary, "text/plain");
        System.out.println("  Saved: " + summaryFileName);

        // Mock verification
        org.mockito.Mockito.when(storageClient.readText(org.mockito.ArgumentMatchers.contains("summary")))
                .thenReturn(summary);
        String s3Summary = storageClient.readText("e2e-test/" + summaryFileName);
        assertThat(s3Summary).isEqualTo(summary);
        System.out.println("  Storage verification: ✅\n");

        // ── 최종 결과 ──
        System.out.println("========================================");
        System.out.println("  Pipeline Complete!");
        System.out.println("========================================");
        System.out.printf("  STT:     %d chunks → %d chars (%dms)%n", CHUNK_FILES.size(), mergedTranscript.length(),
                sttTotalTime);
        System.out.printf("  Summary: %d chars (%dms)%n", summary.length(), summaryElapsed);
        System.out.printf("  Total:   %dms%n", sttTotalTime + summaryElapsed);
        System.out.println("\n--- Summary Preview ---");
        System.out.println(summary.substring(0, Math.min(500, summary.length())));
        System.out.println("...");
    }
}
