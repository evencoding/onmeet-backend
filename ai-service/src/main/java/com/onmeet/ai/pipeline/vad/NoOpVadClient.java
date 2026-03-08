package com.onmeet.ai.pipeline.vad;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * VAD 비활성화 시 사용되는 구현체.
 * 전체 오디오를 하나의 발화 구간으로 반환한다.
 */
@Component
@ConditionalOnProperty(name = "app.vad.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpVadClient implements VadClient {

    @Override
    public List<SpeechSegment> detectSpeech(float[] pcmSamples, int sampleRate) {
        long totalMs = (long) pcmSamples.length * 1000L / sampleRate;
        return List.of(new SpeechSegment(0, totalMs));
    }
}
