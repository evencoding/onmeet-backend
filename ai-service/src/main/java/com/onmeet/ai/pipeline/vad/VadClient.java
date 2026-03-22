package com.onmeet.ai.pipeline.vad;

import java.util.List;

/**
 * VAD(Voice Activity Detection) 인터페이스.
 * PCM 오디오에서 발화 구간을 감지한다.
 */
public interface VadClient {

    /**
     * PCM 오디오 샘플에서 발화(speech) 구간을 감지하여 반환.
     *
     * @param pcmSamples 16kHz, mono, float normalized [-1.0, 1.0]
     * @param sampleRate 샘플레이트 (16000)
     * @return 감지된 발화 구간 리스트 (시간순 정렬)
     */
    List<SpeechSegment> detectSpeech(float[] pcmSamples, int sampleRate);

    /**
     * 발화 구간 정보 (밀리초 단위).
     */
    record SpeechSegment(long startMs, long endMs) {}
}
