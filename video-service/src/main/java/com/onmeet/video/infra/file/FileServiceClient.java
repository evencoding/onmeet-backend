package com.onmeet.video.infra.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * file-service의 S3 파일 등록 API를 호출하는 REST 클라이언트.
 * 이미 MinIO에 존재하는 파일을 file-service에 등록하여 fileId를 발급받습니다.
 */
@Component
public class FileServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FileServiceClient.class);

    private final RestTemplate restTemplate;
    private final String fileServiceUrl;
    private final String gatewaySharedSecret;

    public FileServiceClient(
            RestTemplate restTemplate,
            @Value("${file-service.internal-url:http://file-service:8086}") String fileServiceUrl,
            @Value("${gateway.shared-secret}") String gatewaySharedSecret
    ) {
        this.restTemplate = restTemplate;
        this.fileServiceUrl = fileServiceUrl;
        this.gatewaySharedSecret = gatewaySharedSecret;
    }

    /**
     * 이미 S3에 존재하는 파일을 file-service에 등록하고 fileId를 반환합니다.
     *
     * @return 발급된 fileId
     */
    public Long registerS3File(String s3Key, String fileName, String contentType,
                                Long fileSize, String category, String ownerType, String ownerId) {
        String url = fileServiceUrl + "/file/v1/register-s3";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Gateway-Secret", gatewaySharedSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "s3Key", s3Key,
                "fileName", fileName,
                "contentType", contentType,
                "fileSize", fileSize,
                "category", category,
                "ownerType", ownerType,
                "ownerId", ownerId
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<RegisterS3Response> response = restTemplate.postForEntity(
                    url, entity, RegisterS3Response.class);

            RegisterS3Response responseBody = response.getBody();
            if (responseBody != null && responseBody.success && responseBody.data != null) {
                log.info("Registered S3 file with file-service: s3Key={}, fileId={}", s3Key, responseBody.data.id);
                return responseBody.data.id;
            }
            throw new RuntimeException("file-service returned unexpected response for register-s3: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to register S3 file with file-service: s3Key={}", s3Key, e);
            throw new RuntimeException("file-service S3 파일 등록 실패: s3Key=" + s3Key, e);
        }
    }

    /**
     * file-service API 응답 래퍼 ({ success: true, data: { id, ... } })
     */
    private static class RegisterS3Response {
        @JsonProperty("success")
        public boolean success;

        @JsonProperty("data")
        public FileData data;
    }

    private static class FileData {
        @JsonProperty("id")
        public Long id;

        @JsonProperty("fileName")
        public String fileName;

        @JsonProperty("s3Url")
        public String s3Url;
    }
}
