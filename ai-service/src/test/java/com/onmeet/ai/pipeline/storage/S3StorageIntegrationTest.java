package com.onmeet.ai.pipeline.storage;

import com.onmeet.ai.config.AwsS3Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = {
        "aws.region=ap-northeast-2",
        "aws.s3.bucket-name=onmeet-transcripts",
        "aws.s3.endpoint=http://localhost:4566",
        "aws.credentials.access-key=test",
        "aws.credentials.secret-key=test"
})
@SpringBootTest(classes = { com.onmeet.ai.pipeline.storage.S3StorageClient.class,
        com.onmeet.ai.config.AwsS3Config.class })
@Import(AwsS3Config.class)
@DisplayName("S3 Storage Integration Test (LocalStack)")
class S3StorageIntegrationTest {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3StorageClient storageClient;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private final String testKey = "test/integration-test.txt";
    private final String testContent = "Hello LocalStack S3!";

    @BeforeEach
    void setup() {
        // Ensure bucket exists
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    @AfterEach
    void cleanup() {
        // Cleanup test file
        try {
            storageClient.delete(testKey);
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    @DisplayName("S3 파일 쓰기 및 읽기 테스트")
    void writeAndReadText() {
        // When
        storageClient.writeText(testKey, testContent, "text/plain");

        // Then
        String readContent = storageClient.readText(testKey);
        assertThat(readContent).isEqualTo(testContent);
    }

    @Test
    @DisplayName("S3 바이너리 데이터 쓰기 및 읽기 테스트")
    void writeAndReadBytes() {
        // Given
        byte[] data = "Binary Content".getBytes(StandardCharsets.UTF_8);
        String binaryKey = "test/integration-test.bin";

        try {
            // When
            storageClient.writeBytes(binaryKey, data, "application/octet-stream");

            // Then
            byte[] readData = storageClient.readBytes(binaryKey);
            assertThat(readData).isEqualTo(data);
        } finally {
            storageClient.delete(binaryKey);
        }
    }
}
