package com.onmeet.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.ai.dto.event.ChatMessageEvent;
import com.onmeet.ai.dto.event.TranscriptFinalizedEvent;
import com.onmeet.ai.dto.event.VoiceSegmentCreatedEvent;
import com.onmeet.ai.messaging.producer.TranscriptEventsProducer;
import com.onmeet.ai.pipeline.storage.StorageClient;
import com.onmeet.ai.pipeline.transcript.RedisMeetingEventStore;
import com.onmeet.ai.pipeline.transcript.RedisMeetingEventStore.StoredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptBuilderServiceTest {

    @Mock
    private RedisMeetingEventStore store;

    @Mock
    private StorageClient storageClient;

    @Mock
    private TranscriptEventsProducer producer;

    @Mock
    private ObjectMapper om;

    @InjectMocks
    private TranscriptBuilderService transcriptBuilderService;

    @Test
    @DisplayName("회의가 종료되면 저장된 이벤트를 읽어 트랜스크립트를 생성하고 S3에 저장해야 한다")
    void finalizeMeeting_ShouldCreateTranscriptAndUploadToS3() throws Exception {
        // Given
        Long roomId = 123L;
        Instant endedAt = Instant.ofEpochMilli(1700000000000L);

        ChatMessageEvent chatEvent = ChatMessageEvent.builder()
                .messageId("msg-1")
                .senderId(1L)
                .content("Hello")
                .timestamp(Instant.ofEpochMilli(100L))
                .seq(1)
                .build();
        StoredEvent storedChat = new StoredEvent("CHAT", "msg-1", "1", 1L, "{\"json\":\"chat\"}");

        VoiceSegmentCreatedEvent voiceEvent = VoiceSegmentCreatedEvent.builder()
                .segmentId("seg-1")
                .participantIdentity("참가자2")
                .text("Hi there")
                .segmentStartMs(200L)
                .seq(2L)
                .timestamp(Instant.ofEpochMilli(200L))
                .build();
        StoredEvent storedVoice = new StoredEvent("VOICE", "seg-1", "2", 2L, "{\"json\":\"voice\"}");

        when(store.readAll(roomId)).thenReturn(Arrays.asList(storedChat, storedVoice));

        when(om.readValue(storedChat.getJson(), ChatMessageEvent.class)).thenReturn(chatEvent);
        when(om.readValue(storedVoice.getJson(), VoiceSegmentCreatedEvent.class)).thenReturn(voiceEvent);
        when(om.writeValueAsString(any())).thenReturn("{\"transcript\":\"final\"}");

        // When
        transcriptBuilderService.finalizeMeeting(roomId, endedAt);

        // Then
        verify(storageClient).writeText(contains(String.valueOf(roomId)), eq("{\"transcript\":\"final\"}"), eq("application/json"));

        ArgumentCaptor<TranscriptFinalizedEvent> captor = ArgumentCaptor.forClass(TranscriptFinalizedEvent.class);
        verify(producer).publish(captor.capture());

        TranscriptFinalizedEvent publishedEvent = captor.getValue();
        assertThat(publishedEvent.getRoomId()).isEqualTo(roomId);
        assertThat(publishedEvent.getTranscriptS3Key()).contains(String.valueOf(roomId));

        verify(store).clearMeeting(roomId);
    }

    @Test
    @DisplayName("저장된 이벤트가 없어도 빈 트랜스크립트를 생성하고 종료 처리해야 한다")
    void finalizeMeeting_ShouldHandleEmptyEvents() throws Exception {
        // Given
        Long roomId = 999L;
        when(store.readAll(roomId)).thenReturn(Collections.emptyList());
        when(om.writeValueAsString(any())).thenReturn("{}");

        // When
        transcriptBuilderService.finalizeMeeting(roomId, Instant.now());

        // Then
        verify(storageClient).writeText(anyString(), eq("{}"), eq("application/json"));
        verify(producer).publish(any(TranscriptFinalizedEvent.class));
        verify(store).clearMeeting(roomId);
    }
}
