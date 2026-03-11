package com.onmeet.video.meeting.service.recording;

import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.meeting.entity.recording.RecordingStatus;
import com.onmeet.video.meeting.entity.recording.RecordingType;
import com.onmeet.video.meeting.entity.recording.RoomRecording;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.event.recording.RecordingCompletedEvent;
import com.onmeet.video.meeting.event.recording.RecordingEventProducer;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.recording.RoomRecordingRepository;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.room.RoomSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomRecordingServiceTest {

    @Mock private RoomRecordingRepository recordingRepository;
    @Mock private MeetingRoomRepository roomRepository;
    @Mock private RoomParticipantRepository participantRepository;
    @Mock private RoomSettingsRepository settingsRepository;
    @Mock private LiveKitClient liveKitClient;
    @Mock private ClockProvider clockProvider;
    @Mock private RecordingEventProducer recordingEventProducer;

    private RoomRecordingService service;

    @BeforeEach
    void setUp() {
        service = new RoomRecordingService(
                recordingRepository, roomRepository, participantRepository,
                settingsRepository, liveKitClient, clockProvider, recordingEventProducer
        );
    }

    @Test
    @DisplayName("Egress 완료 시 recording-completed 이벤트가 발행되어야 한다")
    void handleEgressEnded_ShouldPublishRecordingCompletedEvent() {
        String egressId = "egress-abc";
        String s3Path = "/recordings/1/user1/audio_track1.ogg";
        Long fileSizeBytes = 2048L;
        Instant now = Instant.parse("2026-03-11T10:00:00Z");

        MeetingRoom room = mock(MeetingRoom.class);
        when(room.getId()).thenReturn(1L);

        RoomRecording recording = new RoomRecording(
                room, egressId, RecordingType.PARTICIPANT_AUDIO,
                Instant.parse("2026-03-11T09:55:00Z"), "user1", "track1"
        );
        // Set ID via reflection since it's auto-generated
        try {
            var idField = RoomRecording.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(recording, 10L);
        } catch (Exception ignored) {}

        when(recordingRepository.findByEgressId(egressId)).thenReturn(Optional.of(recording));
        when(clockProvider.now()).thenReturn(now);

        service.handleEgressEnded(egressId, s3Path, fileSizeBytes);

        ArgumentCaptor<RecordingCompletedEvent> captor = ArgumentCaptor.forClass(RecordingCompletedEvent.class);
        verify(recordingEventProducer).publishRecordingCompleted(captor.capture());

        RecordingCompletedEvent event = captor.getValue();
        assertThat(event.roomId()).isEqualTo(1L);
        assertThat(event.recordingId()).isEqualTo(10L);
        assertThat(event.egressId()).isEqualTo(egressId);
        assertThat(event.s3Path()).isEqualTo(s3Path);
        assertThat(event.fileSizeBytes()).isEqualTo(fileSizeBytes);
        assertThat(event.participantIdentity()).isEqualTo("user1");
        assertThat(event.trackSid()).isEqualTo("track1");
        assertThat(event.recordingType()).isEqualTo("PARTICIPANT_AUDIO");
    }

    @Test
    @DisplayName("존재하지 않는 egressId일 경우 이벤트가 발행되지 않아야 한다")
    void handleEgressEnded_ShouldNotPublish_WhenRecordingNotFound() {
        when(recordingRepository.findByEgressId(anyString())).thenReturn(Optional.empty());

        service.handleEgressEnded("unknown-egress", "/path", 1024L);

        verify(recordingEventProducer, never()).publishRecordingCompleted(any());
    }

    @Test
    @DisplayName("Egress 실패 시 recording-completed 이벤트가 발행되지 않아야 한다")
    void handleEgressFailed_ShouldNotPublishRecordingCompletedEvent() {
        String egressId = "egress-fail";
        MeetingRoom room = mock(MeetingRoom.class);
        RoomRecording recording = new RoomRecording(
                room, egressId, RecordingType.PARTICIPANT_AUDIO,
                Instant.now(), "user1", "track1"
        );
        when(recordingRepository.findByEgressId(egressId)).thenReturn(Optional.of(recording));
        when(clockProvider.now()).thenReturn(Instant.now());

        service.handleEgressFailed(egressId, "Egress error");

        verify(recordingEventProducer, never()).publishRecordingCompleted(any());
    }
}
