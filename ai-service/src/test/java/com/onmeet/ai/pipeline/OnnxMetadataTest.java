package com.onmeet.ai.pipeline;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;

public class OnnxMetadataTest {
    @Test
    public void inspectModel() throws Exception {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession session = env.createSession("src/main/resources/models/silero_vad.onnx", new OrtSession.SessionOptions())) {
            System.out.println("Input Info:");
            session.getInputInfo().forEach((k, v) -> System.out.println("  Name: " + k + ", Info: " + v));
            System.out.println("Output Info:");
            session.getOutputInfo().forEach((k, v) -> System.out.println("  Name: " + k + ", Info: " + v));
        }
    }
}
