package com.onmeet.ai.pipeline;

import ai.onnxruntime.*;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class SileroVadStandaloneTest {

    @Test
    public void testVadAudio() throws Exception {
        byte[] bytes = Files.readAllBytes(new File("test_output_chunk0.wav").toPath());

        float[] samples;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             AudioInputStream originalStream = AudioSystem.getAudioInputStream(bais)) {
            byte[] pcmBytes = originalStream.readAllBytes();
            int sampleCount = pcmBytes.length / 2;
            ByteBuffer buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
            samples = new float[sampleCount];
            float maxAbs = 0f;
            for (int i = 0; i < sampleCount; i++) {
                samples[i] = buf.getShort() / 32768.0f;
                maxAbs = Math.max(maxAbs, Math.abs(samples[i]));
            }
            if (maxAbs > 0) {
                for (int i = 0; i < sampleCount; i++) {
                    samples[i] /= maxAbs;
                }
                System.out.println("Normalized audio. Max abs was: " + maxAbs);
            }
        }

        System.out.println("Loaded " + samples.length + " samples");

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setInterOpNumThreads(1);
        opts.setIntraOpNumThreads(1);

        OrtSession session = env.createSession("src/main/resources/models/silero_vad.onnx", opts);

        int windowSize = 512;
        int sr = 16000;
        float[] state = new float[2 * 1 * 128]; // [2, 1, 128]

        int triggeredCount = 0;
        int windowCount = samples.length / windowSize;

        for (int i = 0; i < windowCount; i++) {
            float[] window = new float[windowSize];
            System.arraycopy(samples, i * windowSize, window, 0, windowSize);

            long[] inputShape = {1, windowSize};
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(window), inputShape);

            long[] stateShape = {2, 1, 128};
            OnnxTensor stateTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(state), stateShape);

            // Scalar tensor for sr
            OnnxTensor srTensor = OnnxTensor.createTensor(env, (long)sr);

            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put("input", inputTensor);
            inputs.put("state", stateTensor);
            inputs.put("sr", srTensor);

            try (OrtSession.Result result = session.run(inputs)) {
                float prob = ((float[][]) result.get(0).getValue())[0][0];
                if (prob > 0.1f) {
                    triggeredCount++;
                    if (triggeredCount % 100 == 1) {
                        System.out.println("Window " + i + " (" + (i * windowSize / 16000.0) + "s) prob: " + prob);
                    }
                }

                float[][][] newState = (float[][][]) result.get(1).getValue();
                int idx = 0;
                for (int l = 0; l < 2; l++) {
                    for (int b = 0; b < 1; b++) {
                        for (int h = 0; h < 128; h++) {
                            state[idx++] = newState[l][b][h];
                        }
                    }
                }
            } finally {
                inputTensor.close();
                stateTensor.close();
                srTensor.close();
            }
        }

        System.out.println("Triggered windows (>0.1): " + triggeredCount);
    }
}
