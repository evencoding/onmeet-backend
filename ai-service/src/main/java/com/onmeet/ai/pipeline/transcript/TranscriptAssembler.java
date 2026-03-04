package com.onmeet.ai.pipeline.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.pipeline.storage.StorageClient;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.stream.Collectors;

@Component
public class TranscriptAssembler {

    private final StorageClient storageClient;
    private final ObjectMapper om;
    private final TranscriptRenderer renderer;

    public TranscriptAssembler(StorageClient storageClient, ObjectMapper om, TranscriptRenderer renderer) {
        this.storageClient = storageClient;
        this.om = om;
        this.renderer = renderer;
    }

    public TranscriptDocument load(String transcriptS3Key) {
        try {
            String json = storageClient.readText(transcriptS3Key);
            return om.readValue(json, TranscriptDocument.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load transcript: " + transcriptS3Key, e);
        }
    }

    public String assemblePlainText(TranscriptDocument doc) {
        return renderer.toPlainText(doc);
    }
}

