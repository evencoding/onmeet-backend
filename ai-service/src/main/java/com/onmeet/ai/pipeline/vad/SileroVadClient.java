package com.onmeet.ai.pipeline.vad;

import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.*;

/**
 * Silero VAD v4 ONNX 모델을 사용한 Voice Activity Detection 구현.
 * 16kHz mono PCM 오디오에서 발화 구간을 감지한다.
 */
@Component
@ConditionalOnProperty(name = "app.vad.enabled", havingValue = "true")
public class SileroVadClient implements VadClient {

    private static final Logger log = LoggerFactory.getLogger(SileroVadClient.class);
    private static final int WINDOW_SIZE_SAMPLES = 512; // 32ms at 16kHz

    @Value("${app.vad.model-path:classpath:models/silero_vad.onnx}")
    private Resource modelResource;

    @Value("${app.vad.threshold:0.5}")
    private float threshold;

    @Value("${app.vad.min-speech-duration-ms:250}")
    private long minSpeechDurationMs;

    @Value("${app.vad.min-silence-duration-ms:300}")
    private long minSilenceDurationMs;

    @Value("${app.vad.speech-pad-ms:30}")
    private long speechPadMs;

    private OrtEnvironment env;
    private OrtSession session;

    @PostConstruct
    public void init() throws OrtException, IOException {
        env = OrtEnvironment.getEnvironment();
        byte[] modelBytes = modelResource.getInputStream().readAllBytes();
        session = env.createSession(modelBytes, new OrtSession.SessionOptions());
        log.info("Silero VAD model loaded successfully (threshold={})", threshold);
    }

    @PreDestroy
    public void destroy() {
        if (session != null) {
            try { session.close(); } catch (Exception e) { /* ignore */ }
        }
    }

    @Override
    public List<SpeechSegment> detectSpeech(float[] pcmSamples, int sampleRate) {
        if (sampleRate != 16000) {
            throw new IllegalArgumentException("Silero VAD requires 16kHz sample rate, got: " + sampleRate);
        }

        List<SpeechSegment> segments = new ArrayList<>();
        float[] state = new float[2 * 1 * 128]; // 2 layers × 1 batch × 128 hidden
        long[] sr = new long[]{sampleRate};

        boolean inSpeech = false;
        long speechStartSample = 0;
        long lastSpeechEndSample = 0;

        int totalWindows = pcmSamples.length / WINDOW_SIZE_SAMPLES;

        for (int i = 0; i < totalWindows; i++) {
            float[] window = Arrays.copyOfRange(
                    pcmSamples,
                    i * WINDOW_SIZE_SAMPLES,
                    (i + 1) * WINDOW_SIZE_SAMPLES
            );

            float prob = runInference(window, state, sr);

            long currentSample = (long) i * WINDOW_SIZE_SAMPLES;
            long currentMs = (currentSample * 1000L) / sampleRate;

            if (prob >= threshold) {
                if (!inSpeech) {
                    speechStartSample = currentSample;
                    inSpeech = true;
                }
                lastSpeechEndSample = currentSample + WINDOW_SIZE_SAMPLES;
            } else {
                if (inSpeech) {
                    long silenceDurationMs = ((currentSample - lastSpeechEndSample) * 1000L) / sampleRate;
                    if (silenceDurationMs >= minSilenceDurationMs) {
                        long startMs = Math.max(0, (speechStartSample * 1000L) / sampleRate - speechPadMs);
                        long endMs = (lastSpeechEndSample * 1000L) / sampleRate + speechPadMs;
                        long durationMs = endMs - startMs;

                        if (durationMs >= minSpeechDurationMs) {
                            segments.add(new SpeechSegment(startMs, endMs));
                        }
                        inSpeech = false;
                    }
                }
            }
        }

        // 마지막 발화 구간 처리
        if (inSpeech) {
            long startMs = Math.max(0, (speechStartSample * 1000L) / sampleRate - speechPadMs);
            long endMs = Math.min(
                    (long) pcmSamples.length * 1000L / sampleRate,
                    (lastSpeechEndSample * 1000L) / sampleRate + speechPadMs
            );
            if ((endMs - startMs) >= minSpeechDurationMs) {
                segments.add(new SpeechSegment(startMs, endMs));
            }
        }

        log.debug("VAD detected {} speech segments from {} seconds of audio",
                segments.size(), pcmSamples.length / sampleRate);
        return segments;
    }

    private float runInference(float[] window, float[] state, long[] sr) {
        try {
            // Input tensor: [1, windowSize]
            long[] inputShape = {1, window.length};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(window), inputShape);

            // State tensor: [2, 1, 128]
            long[] stateShape = {2, 1, 128};
            OnnxTensor stateTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(state), stateShape);

            // Sample rate tensor
            OnnxTensor srTensor = OnnxTensor.createTensor(env, sr);

            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put("input", inputTensor);
            inputs.put("state", stateTensor);
            inputs.put("sr", srTensor);

            try (OrtSession.Result result = session.run(inputs)) {
                // Output probability
                float[][] output = (float[][]) result.get(0).getValue();
                float prob = output[0][0];

                // Update state from output
                float[][][] newState = (float[][][]) result.get(1).getValue();
                int idx = 0;
                for (int l = 0; l < 2; l++) {
                    for (int b = 0; b < 1; b++) {
                        for (int h = 0; h < 128; h++) {
                            state[idx++] = newState[l][b][h];
                        }
                    }
                }

                return prob;
            } finally {
                inputTensor.close();
                stateTensor.close();
                srTensor.close();
            }
        } catch (OrtException e) {
            log.error("VAD inference failed", e);
            return 0.0f;
        }
    }
}
