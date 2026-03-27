package com.onmeet.ai.pipeline.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * file-service API 호출을 전담하는 클라이언트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.storage.file-service.internal-url:http://file-service:8086}")
    private String fileServiceUrl;

    @Value("${gateway.shared-secret}")
    private String gatewaySecret;

    /**
     * 파일 ID를 통해 바이너리 데이터를 읽어옵니다.
     */
    public byte[] downloadFile(Long fileId) {
        log.debug("Downloading file from file-service: fileId={}", fileId);
        return webClientBuilder.build()
                .get()
                .uri(fileServiceUrl + "/file/render/{id}", fileId)
                .header("X-Gateway-Secret", gatewaySecret)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    /**
     * 바이너리 데이터를 파일 서버에 업로드하고 발급된 ID를 반환합니다.
     */
    public Long uploadFile(byte[] bytes, String filename, String contentType, String category, String ownerType, String ownerId) {
        log.debug("Uploading file to file-service: filename={}, category={}", filename, category);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("files", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.parseMediaType(contentType));

        MultiValueMap<String, HttpEntity<?>> multipartBody = builder.build();

        FileMetadataResponse[] response = webClientBuilder.build()
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path(fileServiceUrl + "/file/upload")
                        .queryParam("category", category)
                        .queryParam("ownerType", ownerType)
                        .queryParam("ownerId", ownerId)
                        .build())
                .header("X-Gateway-Secret", gatewaySecret)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(multipartBody)
                .retrieve()
                .bodyToMono(FileMetadataResponse[].class)
                .timeout(Duration.ofSeconds(60))
                .block();

        if (response != null && response.length > 0) {
            return response[0].getId();
        }
        throw new RuntimeException("Failed to upload file to file-service: No response");
    }

    @Data
    private static class FileMetadataResponse {
        private Long id;
        @JsonProperty("fileName")
        private String fileName;
        @JsonProperty("s3Url")
        private String s3Url;
    }
}
