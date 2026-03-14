package com.onmeet.ai.pipeline.audio;

import com.onmeet.ai.pipeline.vad.VadClient.SpeechSegment;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * 오디오 디코딩 및 인코딩 유틸리티.
 * OGG/WAV → PCM 16kHz mono float 변환 및 발화 구간 WAV 인코딩.
 */
@Component
public class AudioDecoder {

    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final int TARGET_CHANNELS = 1;
    private static final int TARGET_BITS = 16;

    /**
     * 오디오 바이트(OGG/WAV 등)를 16kHz mono float PCM으로 디코딩.
     */
    public float[] decode(byte[] audioBytes) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
             AudioInputStream originalStream = AudioSystem.getAudioInputStream(bais)) {

            AudioFormat originalFormat = originalStream.getFormat();

            // Target: 16kHz, mono, 16-bit signed PCM
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    TARGET_SAMPLE_RATE,
                    TARGET_BITS,
                    TARGET_CHANNELS,
                    TARGET_CHANNELS * (TARGET_BITS / 8),
                    TARGET_SAMPLE_RATE,
                    false // little-endian
            );

            AudioInputStream convertedStream;
            if (originalFormat.matches(targetFormat)) {
                convertedStream = originalStream;
            } else {
                // 2단계 변환이 필요할 수 있음 (코덱 디코딩 → 리샘플링)
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        originalFormat.getSampleRate(),
                        TARGET_BITS,
                        originalFormat.getChannels(),
                        originalFormat.getChannels() * (TARGET_BITS / 8),
                        originalFormat.getSampleRate(),
                        false
                );

                AudioInputStream decodedStream = AudioSystem.getAudioInputStream(decodedFormat, originalStream);
                convertedStream = AudioSystem.getAudioInputStream(targetFormat, decodedStream);
            }

            byte[] pcmBytes = convertedStream.readAllBytes();
            return bytesToFloats(pcmBytes);

        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio format", e);
        }
    }

    /**
     * 특정 발화 구간의 PCM 샘플을 WAV 바이트로 인코딩.
     */
    public byte[] extractAndEncode(float[] pcmSamples, SpeechSegment segment) {
        int startSample = (int) (segment.startMs() * TARGET_SAMPLE_RATE / 1000);
        int endSample = (int) Math.min(segment.endMs() * TARGET_SAMPLE_RATE / 1000, pcmSamples.length);

        if (startSample >= endSample || startSample >= pcmSamples.length) {
            return new byte[0];
        }

        float[] segmentSamples = new float[endSample - startSample];
        System.arraycopy(pcmSamples, startSample, segmentSamples, 0, segmentSamples.length);

        return encodeToWav(segmentSamples);
    }

    /**
     * float PCM 샘플을 WAV 바이트로 인코딩 (16kHz, mono, 16-bit).
     */
    private byte[] encodeToWav(float[] samples) {
        byte[] pcmBytes = floatsToBytes(samples);

        AudioFormat format = new AudioFormat(
                TARGET_SAMPLE_RATE, TARGET_BITS, TARGET_CHANNELS, true, false
        );

        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmBytes);
             AudioInputStream audioStream = new AudioInputStream(bais, format, samples.length);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, baos);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode WAV", e);
        }
    }

    /**
     * 16-bit LE PCM 바이트 → float [-1.0, 1.0] 변환.
     */
    private float[] bytesToFloats(byte[] pcmBytes) {
        int sampleCount = pcmBytes.length / 2;
        float[] samples = new float[sampleCount];
        ByteBuffer buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);

        float maxAbs = 0f;
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = buf.getShort() / 32768.0f;
            float absVal = Math.abs(samples[i]);
            if (absVal > maxAbs) {
                maxAbs = absVal;
            }
        }

        // 정규화 (Normalization): 최대 진폭을 1.0으로 맞춤
        if (maxAbs > 0.0001f) {
            for (int i = 0; i < sampleCount; i++) {
                samples[i] /= maxAbs;
            }
            System.out.println("  [AudioDecoder] Normalized audio with max amplitude: " + maxAbs);
        } else {
            System.out.println("  [AudioDecoder] Audio is too quiet to normalize (max: " + maxAbs + ")");
        }

        return samples;
    }

    /**
     * float [-1.0, 1.0] → 16-bit LE PCM 바이트 변환.
     */
    private byte[] floatsToBytes(float[] samples) {
        ByteBuffer buf = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float s : samples) {
            short val = (short) Math.max(-32768, Math.min(32767, s * 32768.0f));
            buf.putShort(val);
        }
        return buf.array();
    }
}
