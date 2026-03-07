package com.onmeet.ai.pipeline.storage;

public interface StorageClient {

    // Text (JSON, txt)
    void writeText(String key, String text, String contentType);

    String readText(String key);

    // Binary (audio chunk, wav/mp3, etc.)
    void writeBytes(String key, byte[] bytes, String contentType);

    byte[] readBytes(String key);

    void delete(String key);
}