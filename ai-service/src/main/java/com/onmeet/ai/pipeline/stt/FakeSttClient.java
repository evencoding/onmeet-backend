package com.onmeet.ai.pipeline.stt;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({ "test", "local", "docker" })
@Component
public class FakeSttClient implements SttClient {
    @Override
    public String transcribe(byte[] audioBytes, String filename, String mimeType) {
        return "fake transcription";
    }
}
