package com.onmeet.ai.pipeline.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.AiErrorCode;
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
        return loadInternal(storageClient.readText(transcriptS3Key));
    }

    public TranscriptDocument load(Long fileId) {
        return loadInternal(storageClient.readText(fileId));
    }

    private TranscriptDocument loadInternal(String json) {
        try {
            return om.readValue(json, TranscriptDocument.class);
        } catch (Exception e) {
            throw new BusinessException(AiErrorCode.TRANSCRIPT_PARSE_FAILED);
        }
    }

    public String assemblePlainText(TranscriptDocument doc) {
        return renderer.toPlainText(doc);
    }
}

