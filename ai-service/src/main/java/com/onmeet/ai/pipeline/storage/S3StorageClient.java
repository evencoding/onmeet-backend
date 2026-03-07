package com.onmeet.ai.pipeline.storage;

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
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        s3.putObject(req, RequestBody.fromBytes(bytes));
    }

    @Override
    public byte[] readBytes(String key) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        ResponseBytes<GetObjectResponse> resp = s3.getObjectAsBytes(req);
        return resp.asByteArray();
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }
}
