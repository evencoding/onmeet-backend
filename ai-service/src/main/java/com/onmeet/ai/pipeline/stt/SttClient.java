package com.onmeet.ai.pipeline.stt;

public interface SttClient {
    /**
     * @param audioBytes 오디오 바이너리
     * @param filename   확장자 포함 파일명 (예: chunk.webm)
     * @param mimeType   예: audio/webm
     * @return text
     */
    String transcribe(byte[] audioBytes, String filename, String mimeType);
}
