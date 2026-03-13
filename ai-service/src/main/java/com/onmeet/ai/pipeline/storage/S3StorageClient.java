package com.onmeet.ai.pipeline.storage;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.AiErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;

@Component
public class S3StorageClient implements StorageClient {

    private final S3Client s3;
    private final String bucket;

    public S3StorageClient(S3Client s3,
            @Value("${aws.s3.bucket-name}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void writeText(String key, String text, String contentType) {
        writeBytes(key, text.getBytes(StandardCharsets.UTF_8), contentType);
    }

    @Override
    public String readText(String key) {
        return new String(readBytes(key), StandardCharsets.UTF_8);
    }

    @Override
    public void writeBytes(String key, byte[] bytes, String contentType) {
        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3.putObject(req, RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            // TODO: [AI][AiErrorCode.S3_UPLOAD_FAILED] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.S3_UPLOAD_FAILED);
        }
    }

    @Override
    public byte[] readBytes(String key) {
        try {
            GetObjectRequest req = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> resp = s3.getObjectAsBytes(req);
            return resp.asByteArray();
        } catch (NoSuchKeyException e) {
            // TODO: [AI][AiErrorCode.S3_FILE_NOT_FOUND] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.S3_FILE_NOT_FOUND);
        } catch (S3Exception e) {
            // TODO: [AI][AiErrorCode.S3_CLIENT_ERROR] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.S3_CLIENT_ERROR);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            // TODO: [AI][AiErrorCode.S3_CLIENT_ERROR] 에러메시지 검수 요청
            throw new BusinessException(AiErrorCode.S3_CLIENT_ERROR);
        }
    }
}
