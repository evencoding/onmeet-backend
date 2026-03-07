package com.onmeet.ai.pipeline.stt;

import com.onmeet.ai.config.AwsS3Config;
import com.onmeet.ai.pipeline.storage.S3StorageClient;
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
 *
 * assembly_chunk_00 ~ 05 파일을 각각 gpt-4o-mini-transcribe로 변환하고,
 * 개별 결과 + 병합 결과를 텍스트 파일로 저장합니다.
 */
@ActiveProfiles("test")
@SpringBootTest(classes = {
        OpenAiSttClient.class,
        AwsS3Config.class,
        S3StorageClient.class
})
@Import(WebClientAutoConfiguration.class)
@DisplayName("Chunked Audio STT Integration Test")
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

    @Autowired
    private S3StorageClient storageClient;

    @Autowired
    private S3Client s3Client;

    private final String bucketName = "onmeet-transcripts";

    // 청크 파일 목록
    private static final List<String> CHUNK_FILES = Arrays.asList(
            "assembly_chunk_00_10min.m4a",
            "assembly_chunk_01_10min.m4a",
            "assembly_chunk_02_10min.m4a",
            "assembly_chunk_03_10min.m4a",
            "assembly_chunk_05_10min.m4a");

    // 결과 파일 저장 경로 (stt_test 폴더)
    private static final String OUTPUT_DIR = "src/test/resources/stt_test";

    @BeforeEach
    void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    @Test
    @DisplayName("Chunked Audio Files -> gpt-4o-mini-transcribe -> Individual & Merged Results")
    void testChunkedSttPipeline() throws IOException {
        List<String> allTranscripts = new ArrayList<>();
        Path outputPath = Paths.get(System.getProperty("user.dir"), OUTPUT_DIR);

        System.out.println("=== Chunked STT Pipeline Start ===");
        System.out.println("Output directory: " + outputPath.toAbsolutePath());
        System.out.println("Total chunks: " + CHUNK_FILES.size());
        System.out.println();

        for (int i = 0; i < CHUNK_FILES.size(); i++) {
            String chunkFile = CHUNK_FILES.get(i);
            System.out.println("--- Processing chunk " + i + ": " + chunkFile + " ---");

            // 1. 파일 로드
            ClassPathResource resource = new ClassPathResource("stt_test/" + chunkFile);
            assertThat(resource.exists())
                    .as("Chunk file must exist: " + chunkFile)
                    .isTrue();

            byte[] audioBytes = StreamUtils.copyToByteArray(resource.getInputStream());
            System.out.println("  File size: " + (audioBytes.length / 1024) + " KB");

            // 2. STT 변환 (gpt-4o-mini-transcribe)
            long startTime = System.currentTimeMillis();
            String transcript = sttClient.transcribe(audioBytes, chunkFile, "audio/m4a");
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(transcript).isNotNull().isNotEmpty();
            System.out.println("  Transcription time: " + elapsed + "ms");
            System.out.println("  Result length: " + transcript.length() + " chars");
            System.out.println("  Preview: " + transcript.substring(0, Math.min(100, transcript.length())) + "...");
            System.out.println();

            allTranscripts.add(transcript);

            // 3. 개별 결과 파일 저장
            String resultFileName = chunkFile.replace(".m4a", "_result.txt");
            Path resultPath = outputPath.resolve(resultFileName);
            Files.writeString(resultPath, transcript, StandardCharsets.UTF_8);
            System.out.println("  Saved: " + resultFileName);

            // 4. S3에도 저장
            String s3Key = "chunked-stt-test/" + resultFileName;
            storageClient.writeText(s3Key, transcript, "text/plain");
            System.out.println("  S3 saved: " + s3Key);
            System.out.println();
        }

        // 5. 전체 병합 결과 저장
        StringBuilder merged = new StringBuilder();
        for (int i = 0; i < allTranscripts.size(); i++) {
            merged.append("=== Chunk ").append(i).append(" (").append(CHUNK_FILES.get(i)).append(") ===\n");
            merged.append(allTranscripts.get(i));
            merged.append("\n\n");
        }

        // 구분자 없는 순수 텍스트 병합본도 생성
        String mergedPlainText = String.join("\n", allTranscripts);

        // 병합 결과 파일 저장 (구분자 포함)
        Path mergedPath = outputPath.resolve("assembly_chunks_merged_result.txt");
        Files.writeString(mergedPath, merged.toString(), StandardCharsets.UTF_8);
        System.out.println("=== Merged result saved: assembly_chunks_merged_result.txt ===");

        // 순수 텍스트 병합본 저장
        Path mergedPlainPath = outputPath.resolve("assembly_chunks_merged_plain.txt");
        Files.writeString(mergedPlainPath, mergedPlainText, StandardCharsets.UTF_8);
        System.out.println("=== Plain merged result saved: assembly_chunks_merged_plain.txt ===");

        // S3에도 병합본 저장
        storageClient.writeText("chunked-stt-test/merged_result.txt", merged.toString(), "text/plain");

        // 6. 검증
        System.out.println();
        System.out.println("=== Summary ===");
        System.out.println("Chunks processed: " + allTranscripts.size());
        System.out.println("Total characters: " + mergedPlainText.length());
        System.out.println("Merged file size: " + merged.length() + " bytes");

        assertThat(allTranscripts).hasSize(CHUNK_FILES.size());
        assertThat(mergedPlainText).isNotEmpty();
    }
}
