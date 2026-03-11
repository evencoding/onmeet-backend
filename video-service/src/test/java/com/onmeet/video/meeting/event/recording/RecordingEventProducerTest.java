package com.onmeet.video.meeting.event.recording;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private RecordingEventProducer producer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        producer = new RecordingEventProducer(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("녹음 완료 이벤트가 올바른 토픽과 키로 발행되어야 한다")
    void publishRecordingCompleted_ShouldSendToCorrectTopicAndKey() {
        Instant now = Instant.now();
        RecordingCompletedEvent event = new RecordingCompletedEvent(
                100L, 1L, "egress-123",
                "/recordings/100/user1/audio_track1.ogg",
                1024L, 300, "user1", "track1",
                "PARTICIPANT_AUDIO", now, now, now
        );

        producer.publishRecordingCompleted(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("recording-completed"), keyCaptor.capture(), valueCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("100");
        assertThat(valueCaptor.getValue()).isInstanceOf(String.class);
        String json = (String) valueCaptor.getValue();
        assertThat(json).contains("\"roomId\":100");
        assertThat(json).contains("\"recordingId\":1");
        assertThat(json).contains("\"s3Path\":\"/recordings/100/user1/audio_track1.ogg\"");
    }

    @Test
    @DisplayName("직렬화 성공 시 KafkaTemplate.send가 정확히 1번 호출되어야 한다")
    void publishRecordingCompleted_ShouldCallSendExactlyOnce() {
        Instant now = Instant.now();
        RecordingCompletedEvent event = new RecordingCompletedEvent(
                200L, 2L, "egress-456",
                "/recordings/200/user2/audio_track2.ogg",
                2048L, 600, "user2", "track2",
                "PARTICIPANT_AUDIO", now, now, now
        );

        producer.publishRecordingCompleted(event);

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }
}
