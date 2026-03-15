package com.onmeet.ai.pipeline.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * file-service를 저장소로 사용하는 StorageClient 구현체
 */
@Slf4j
@Component
@Primary // 기존 S3 기동 시에도 이 구현체를 우선 사용하도록 설정
@RequiredArgsConstructor
public class FileServerStorageClient implements StorageClient {

    private final FileServiceClient fileServiceClient;

    @Override
    public String writeText(String key, String text, String contentType) {
        // 기존 S3 Key 기반 인터페이스 호환을 위해 파일명으로 추출 시도
        String filename = key.contains("/") ? key.substring(key.lastIndexOf("/") + 1) : key;
        return writeText(filename, text, contentType, "summary", "SYSTEM", "SYSTEM");
    }

    @Override
    public String writeText(String filename, String text, String contentType, String category, String ownerType, String ownerId) {
        Long id = fileServiceClient.uploadFile(text.getBytes(StandardCharsets.UTF_8), filename, contentType, category, ownerType, ownerId);
        return String.valueOf(id);
    }

    @Override
    public String readText(String key) {
        throw new UnsupportedOperationException("FileServerStorageClient does not support raw S3 key access for reading text.");
    }

    @Override
    public String readText(Long fileId) {
        byte[] bytes = fileServiceClient.downloadFile(fileId);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String writeBytes(String key, byte[] bytes, String contentType) {
        String filename = key.contains("/") ? key.substring(key.lastIndexOf("/") + 1) : key;
        return writeBytes(filename, bytes, contentType, "audio", "SYSTEM", "SYSTEM");
    }

    @Override
    public String writeBytes(String filename, byte[] bytes, String contentType, String category, String ownerType, String ownerId) {
        Long id = fileServiceClient.uploadFile(bytes, filename, contentType, category, ownerType, ownerId);
        return String.valueOf(id);
    }

    @Override
    public byte[] readBytes(String key) {
        // 하이브리드 지원: key가 숫자로만 되어 있으면 ID로 간주하여 시도 (임시)
        try {
            Long fileId = Long.parseLong(key);
            return readBytes(fileId);
        } catch (NumberFormatException e) {
            throw new UnsupportedOperationException("FileServerStorageClient requires fileId for reading bytes. Provided key: " + key);
        }
    }

    @Override
    public byte[] readBytes(Long fileId) {
        return fileServiceClient.downloadFile(fileId);
    }

    @Override
    public void delete(String key) {
        // file-service의 delete API 호출 구현 필요 (필요 시)
        log.warn("Delete by S3 key is not supported in FileServerStorageClient: {}", key);
    }

    @Override
    public void delete(Long fileId) {
        // file-service의 delete API 호출 구현 필요 (필요 시)
        log.warn("Delete by fileId is not yet implemented: {}", fileId);
    }
}
