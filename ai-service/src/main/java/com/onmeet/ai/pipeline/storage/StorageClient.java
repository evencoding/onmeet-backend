package com.onmeet.ai.pipeline.storage;

public interface StorageClient {

    // Text (JSON, txt)
    String writeText(String key, String text, String contentType);
    String writeText(String filename, String text, String contentType, String category, String ownerType, String ownerId);

    String readText(String key);
    String readText(Long fileId);

    // Binary (audio chunk, wav/mp3, etc.)
    String writeBytes(String key, byte[] bytes, String contentType);
    String writeBytes(String filename, byte[] bytes, String contentType, String category, String ownerType, String ownerId);

    byte[] readBytes(String key);
    byte[] readBytes(Long fileId);

    void delete(String key);
    void delete(Long fileId);
}