package com.onmeet.ai.pipeline.stt;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// CHECK [ai-담당자]: FakeSttClient 프로필에서 "docker"가 제거됨.
// docker 프로필에서는 OpenAiSttClient(ConditionalOnProperty)가 활성화됨.
@Profile({ "test", "local" })
@Component
public class FakeSttClient implements SttClient {
    @Override
    public String transcribe(byte[] audioBytes, String filename, String mimeType) {
        return "fake transcription";
    }
}
