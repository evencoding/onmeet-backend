package com.onmeet.infra.external;

public interface StorageClient {
    String upload(String path, byte[] bytes);
}
