package com.onmeet.ai.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.config.AwsS3Config;
import com.onmeet.ai.dto.event.AudioChunkReadyEvent;
import com.onmeet.ai.pipeline.audio.AudioDecoder;
import com.onmeet.ai.pipeline.nlp.ClaudeSummarizerClient;
import com.onmeet.ai.pipeline.storage.S3StorageClient;
import com.onmeet.ai.pipeline.storage.StorageKeyFactory;
import com.onmeet.ai.pipeline.stt.OpenAiSttClient;
import com.onmeet.ai.pipeline.vad.SileroVadClient;
import com.onmeet.ai.service.SttWorkerService;
import com.onmeet.ai.service.SummaryWorkerService;
import com.onmeet.ai.service.TranscriptBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * m4a 실제 음성 파일을 VAD를 거쳐 분할한 뒤 STT -> 요약까지 수행하는 E2E 풀 테스트
 * (JAVE 라이브러리를 이용하여 m4a를 16kHz mono wav로 변환한 후 VAD에 주입합니다)
 */
@ActiveProfiles("test")
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        "audio-chunk-ready", "voice-segment-created", 
        "transcript-finalized", "meeting-ended", 
        "chat-events", "minutes-generated"
})
@TestPropertySource(properties = {
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
        "app.vad.enabled=true",
        "app.vad.model-path=classpath:models/silero_vad.onnx",
        "app.vad.threshold=0.1",
        "app.vad.min-speech-duration-ms=250",
        "gateway.shared-secret=test-secret"
})
@DisplayName("Real Audio VAD Pipeline E2E Test")
class RealAudioPipelineE2ETest {

    @Autowired
    private SttWorkerService sttWorkerService;

    @Autowired
    private TranscriptBuilderService transcriptBuilderService;

    @Autowired
    private SummaryWorkerService summaryWorkerService;

    @Autowired
    private S3StorageClient storageClient;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private ObjectMapper om;

    private final String bucketName = "onmeet-transcripts";

    // 5개의 기존 청크 데이터셋
    private static final List<String> CHUNK_FILES = Arrays.asList(
            "assembly_chunk_00_10min.m4a",
            "assembly_chunk_01_10min.m4a",
            "assembly_chunk_02_10min.m4a",
            "assembly_chunk_03_10min.m4a",
            "assembly_chunk_05_10min.m4a"
    // 테스트가 너무 오래 걸리는 것을 방지하기 위해 2개만 테스트하거나, 필요시 List 전부 사용 (주석처리됨)
    );

    private static final String OUTPUT_DIR = "src/test/resources/e2e_test/vad_real";

    @BeforeEach
    void setup() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    @Test
    void executePipelineWithRealAudio() throws Exception {
        Long roomId = System.currentTimeMillis();
        String participantId = "test-speaker";

        Path outputPath = Paths.get(System.getProperty("user.dir"), OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        System.out.println("==============================================");
        System.out.println("  Real Audio Pipeline E2E Test (VAD -> STT)   ");
        System.out.println("==============================================\n");

        for (int i = 0; i < CHUNK_FILES.size(); i++) {
            String m4aFile = CHUNK_FILES.get(i);
            File sourceFile = new ClassPathResource("stt_test/" + m4aFile).getFile();

            // 1. 원본 m4a 파일을 wav로 변환 후 가상의 S3 경로에 업로드 (VAD 정상 동작 유도)
            File wavFile = File.createTempFile("converted_chunk_" + i, ".wav");
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("pcm_s16le");
            audio.setChannels(1);
            audio.setSamplingRate(16000);

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("wav");
            attrs.setAudioAttributes(audio);

            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(sourceFile), wavFile, attrs);

            byte[] wavBytes = Files.readAllBytes(wavFile.toPath());
            System.out.println("  -> Converted WAV size: " + wavBytes.length + " bytes");

            // 디버깅 목적으로 첫 번째 chunk를 로컬에 저장
            if (i == 0) {
                Files.write(new File("test_output_chunk0.wav").toPath(), wavBytes);
            }

            String s3Key = "audio-chunks/" + roomId + "/" + participantId + "/" + i + ".wav";
            storageClient.writeBytes(s3Key, wavBytes, "audio/wav");
            
            wavFile.delete(); // 임시파일 정리

            // 3. SttWorkerService 의 handleAudioChunk 호출 (디코딩 -> VAD 자르기 -> STT -> 이벤트 발행)
            System.out.println("▶ VAD & STT Processing: " + m4aFile);
            long startMs = i * 600000L; // 가상의 청크 시작시간 지정 (10분 단위)

            AudioChunkReadyEvent chunkEvent = AudioChunkReadyEvent.builder()
                    .roomId(roomId)
                    .participantIdentity(participantId)
                    .segmentIndex(i)
                    .s3Path(s3Key)
                    .startTime(Instant.ofEpochMilli(startMs))
                    .endTime(Instant.ofEpochMilli(startMs + 600000L))
                    .build();

            sttWorkerService.handleAudioChunk(chunkEvent);
        }

        System.out.println("\n▶ Waiting for Kafka Consumers and STT API (30sec)...");
        Thread.sleep(30000); // STT가 완료되고 카프카 이벤트가 처리될 때까지 대기

        System.out.println("\n▶ Finalizing Transcript & Summary...");

        // 4. 트랜스크립트 취합 (Redis 버퍼 -> S3 JSON)
        transcriptBuilderService.finalizeMeeting(roomId, Instant.now());

        // S3에서 트랜스크립스 문서 다운로드 후 검증
        String transcriptKey = StorageKeyFactory.transcriptKey(roomId, "ignored").replace("ignored", "");
        // Note: It's generated inside UUID, let's just find the file from S3 bucket
        var s3Response = s3Client.listObjectsV2(b -> b.bucket(bucketName).prefix("transcripts/" + roomId));
        assertThat(s3Response.contents()).isNotEmpty();

        String savedTranscriptKey = s3Response.contents().get(0).key();
        String transcriptJson = storageClient.readText(savedTranscriptKey);

        Files.writeString(outputPath.resolve("final_transcript.json"), transcriptJson, StandardCharsets.UTF_8);
        System.out.println("  Saved final transcript JSON to: final_transcript.json");

        // 5. 요약 워커 수동 실행 (원래는 Kafka Consumer가 하지만 E2E 테스트를 위해 직접 호출)
        // TranscriptFinalizedEvent 흉내
        String txnId = savedTranscriptKey.substring(savedTranscriptKey.lastIndexOf('/') + 1,
                savedTranscriptKey.lastIndexOf('.'));

        com.onmeet.ai.dto.event.TranscriptFinalizedEvent tfEvent = com.onmeet.ai.dto.event.TranscriptFinalizedEvent
                .builder()
                .roomId(roomId)
                .transcriptId(txnId)
                .transcriptS3Key(savedTranscriptKey)
                .version(1)
                .finalizedAt(Instant.now())
                .build();

        summaryWorkerService.handleTranscriptFinalized(tfEvent);

        // 6. 요약 저장 검증 (SummaryResult JSON 형식)
        String summaryKey = StorageKeyFactory.summaryKey(roomId, txnId);
        String summaryJson = storageClient.readText(summaryKey);

        Files.writeString(outputPath.resolve("final_summary.json"), summaryJson, StandardCharsets.UTF_8);
        System.out.println("  Saved final summary JSON to: final_summary.json\n");

        System.out.println("--- Summary JSON Preview ---");
        System.out.println(summaryJson);
        System.out.println("-----------------------------");

        assertThat(summaryJson).contains("description");
        assertThat(summaryJson).doesNotContain("timestamp"); // 시간 정보 없음 보장 확인
    }
}
