package com.onmeet.video.meeting.service.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.ParticipantInfo;
import com.onmeet.video.infra.livekit.LiveKitClient.TrackInfo;
import com.onmeet.video.meeting.dto.recording.RoomRecordingResponse;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.entity.participant.RoomParticipant;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.entity.recording.RecordingStatus;
import com.onmeet.video.meeting.entity.recording.RecordingType;
import com.onmeet.video.meeting.entity.recording.RoomRecording;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomSettings;
import com.onmeet.video.meeting.entity.room.RoomType;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.recording.RoomRecordingRepository;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.room.RoomSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomRecordingServiceTest {

    @Mock RoomRecordingRepository recordingRepository;
    @Mock MeetingRoomRepository roomRepository;
    @Mock RoomParticipantRepository participantRepository;
    @Mock RoomSettingsRepository settingsRepository;
    @Mock LiveKitClient liveKitClient;
    @Mock ClockProvider clockProvider;
    @Mock MeetingEventPublisher eventPublisher;
    @Mock AuthServiceClient authServiceClient;

    @InjectMocks
    RoomRecordingService recordingService;

    private static final Long HOST_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-03-22T10:00:00Z");

    private MeetingRoom createActiveRoom() {
        MeetingRoom room = new MeetingRoom("Test Room", "desc", HOST_ID,
                RoomType.INSTANT, 10, null, null, RoomAccessScope.ALL, null);
        room.start(NOW.minusSeconds(3600));
        return room;
    }

    @Nested
    @DisplayName("startRecording")
    class StartRecording {

        @Test
        @DisplayName("녹화 시작 성공 - 참가자별 트랙 녹화")
        void startRecording_success() {
            MeetingRoom room = createActiveRoom();
            RoomSettings settings = RoomSettings.createDefault(room);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(settingsRepository.findByRoomId(ROOM_ID)).thenReturn(Optional.of(settings));
            when(recordingRepository.findByRoomIdAndStatus(ROOM_ID, RecordingStatus.RECORDING))
                    .thenReturn(List.of());
            when(clockProvider.now()).thenReturn(NOW);

            List<ParticipantInfo> participants = List.of(
                    new ParticipantInfo("1", "User1", List.of(
                            new TrackInfo("TR_1", "MICROPHONE"),
                            new TrackInfo("TR_2", "CAMERA"))),
                    new ParticipantInfo("2", "User2", List.of(
                            new TrackInfo("TR_3", "MICROPHONE"))));

            when(liveKitClient.listParticipants(room.getLivekitRoomName())).thenReturn(participants);
            when(liveKitClient.startTrackEgress(anyString(), anyString(), anyString())).thenReturn("egress-id");
            when(recordingRepository.save(any(RoomRecording.class))).thenAnswer(inv -> inv.getArgument(0));

            recordingService.startRecording(ROOM_ID, HOST_ID);

            // MICROPHONE 트랙만 녹화: User1(TR_1) + User2(TR_3) = 2번
            verify(liveKitClient, times(2)).startTrackEgress(anyString(), anyString(), anyString());
            verify(recordingRepository, times(2)).save(any(RoomRecording.class));
        }

        @Test
        @DisplayName("비활성 방에서 녹화 시작 시 예외")
        void startRecording_roomNotActive_throws() {
            MeetingRoom room = new MeetingRoom("Test Room", "desc", HOST_ID,
                    RoomType.INSTANT, 10, null, null, RoomAccessScope.ALL, null);
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> recordingService.startRecording(ROOM_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("이미 녹화 중인 경우 예외")
        void startRecording_alreadyRecording_throws() {
            MeetingRoom room = createActiveRoom();
            RoomSettings settings = RoomSettings.createDefault(room);
            RoomRecording existing = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(settingsRepository.findByRoomId(ROOM_ID)).thenReturn(Optional.of(settings));
            when(recordingRepository.findByRoomIdAndStatus(ROOM_ID, RecordingStatus.RECORDING))
                    .thenReturn(List.of(existing));

            assertThatThrownBy(() -> recordingService.startRecording(ROOM_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("stopRecording")
    class StopRecording {

        @Test
        @DisplayName("녹화 중지 성공")
        void stopRecording_success() {
            MeetingRoom room = createActiveRoom();
            RoomRecording rec1 = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW);
            RoomRecording rec2 = new RoomRecording(room, "eg-2", RecordingType.PARTICIPANT_AUDIO, NOW);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(recordingRepository.findByRoomIdAndStatus(ROOM_ID, RecordingStatus.RECORDING))
                    .thenReturn(List.of(rec1, rec2));

            recordingService.stopRecording(ROOM_ID, HOST_ID);

            assertThat(rec1.getStatus()).isEqualTo(RecordingStatus.PROCESSING);
            assertThat(rec2.getStatus()).isEqualTo(RecordingStatus.PROCESSING);
            verify(liveKitClient).stopEgress("eg-1");
            verify(liveKitClient).stopEgress("eg-2");
        }

        @Test
        @DisplayName("활성 녹화 없을 시 예외")
        void stopRecording_noActive_throws() {
            MeetingRoom room = createActiveRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(recordingRepository.findByRoomIdAndStatus(ROOM_ID, RecordingStatus.RECORDING))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> recordingService.stopRecording(ROOM_ID, HOST_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("startParticipantTrackEgress")
    class StartParticipantTrackEgress {

        @Test
        @DisplayName("후발 참가자 녹화 시작 - 녹화 중일 때만 동작")
        void startParticipantTrackEgress_whenRecording() {
            MeetingRoom room = createActiveRoom();
            RoomRecording existing = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW);

            when(recordingRepository.findByRoomIdAndStatus(ROOM_ID, RecordingStatus.RECORDING))
                    .thenReturn(List.of(existing));
            when(recordingRepository.findByRoomIdAndTrackSidAndStatus(ROOM_ID, "TR_NEW", RecordingStatus.RECORDING))
                    .thenReturn(Optional.empty());
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(clockProvider.now()).thenReturn(NOW);
            when(liveKitClient.startTrackEgress(anyString(), eq("TR_NEW"), anyString())).thenReturn("eg-new");
            when(recordingRepository.save(any(RoomRecording.class))).thenAnswer(inv -> inv.getArgument(0));

            recordingService.startParticipantTrackEgress(ROOM_ID, "user3", "TR_NEW");

            verify(liveKitClient).startTrackEgress(anyString(), eq("TR_NEW"), anyString());
        }

        @Test
        @DisplayName("녹화 중이 아니면 무시")
        void startParticipantTrackEgress_notRecording_skips() {
            when(recordingRepository.findByRoomIdAndStatus(ROOM_ID, RecordingStatus.RECORDING))
                    .thenReturn(List.of());

            recordingService.startParticipantTrackEgress(ROOM_ID, "user3", "TR_NEW");

            verify(liveKitClient, never()).startTrackEgress(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("handleEgress callbacks")
    class EgressCallbacks {

        @Test
        @DisplayName("녹화 완료 처리")
        void handleEgressEnded_success() {
            MeetingRoom room = createActiveRoom();
            RoomRecording recording = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW, "1", "TR_1");

            when(recordingRepository.findByEgressId("eg-1")).thenReturn(Optional.of(recording));
            when(clockProvider.now()).thenReturn(NOW.plusSeconds(600));

            recordingService.handleEgressEnded("eg-1", "/recordings/10/1/audio.ogg", 1024L);

            assertThat(recording.getStatus()).isEqualTo(RecordingStatus.COMPLETED);
            assertThat(recording.getS3Path()).isEqualTo("/recordings/10/1/audio.ogg");
            assertThat(recording.getFileSizeBytes()).isEqualTo(1024L);
        }

        @Test
        @DisplayName("녹화 실패 처리")
        void handleEgressFailed_success() {
            MeetingRoom room = createActiveRoom();
            RoomRecording recording = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW);

            when(recordingRepository.findByEgressId("eg-1")).thenReturn(Optional.of(recording));
            when(clockProvider.now()).thenReturn(NOW.plusSeconds(60));

            recordingService.handleEgressFailed("eg-1", "Network error");

            assertThat(recording.getStatus()).isEqualTo(RecordingStatus.FAILED);
            assertThat(recording.getErrorMessage()).isEqualTo("Network error");
        }
    }

    @Nested
    @DisplayName("getDownloadUrl")
    class GetDownloadUrl {

        @Test
        @DisplayName("완료된 녹화 다운로드 URL 반환")
        void getDownloadUrl_success() {
            MeetingRoom room = createActiveRoom();
            RoomRecording recording = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW);
            recording.markCompleted("/recordings/10/1/audio.ogg", 2048L, NOW.plusSeconds(300));

            when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));

            String url = recordingService.getDownloadUrl(1L);

            assertThat(url).isEqualTo("/recordings/10/1/audio.ogg");
        }

        @Test
        @DisplayName("미완료 녹화 다운로드 시도 시 예외")
        void getDownloadUrl_notCompleted_throws() {
            MeetingRoom room = createActiveRoom();
            RoomRecording recording = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW);

            when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));

            assertThatThrownBy(() -> recordingService.getDownloadUrl(1L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("deleteRecording")
    class DeleteRecording {

        @Test
        @DisplayName("녹화 삭제 성공 - 녹화 중이면 egress 정지")
        void deleteRecording_recording_stopsEgress() {
            MeetingRoom room = createActiveRoom();
            RoomRecording recording = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW);

            when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));

            recordingService.deleteRecording(1L, HOST_ID);

            verify(liveKitClient).stopEgress("eg-1");
            verify(recordingRepository).delete(recording);
        }

        @Test
        @DisplayName("녹화 삭제 성공 - 완료 상태면 egress 정지 안함")
        void deleteRecording_completed_noEgressStop() {
            MeetingRoom room = createActiveRoom();
            RoomRecording recording = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW);
            recording.markCompleted("/path", 100L, NOW.plusSeconds(60));

            when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));

            recordingService.deleteRecording(1L, HOST_ID);

            verify(liveKitClient, never()).stopEgress(anyString());
            verify(recordingRepository).delete(recording);
        }
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("녹화 목록 조회")
        void listRecordings_success() {
            MeetingRoom room = createActiveRoom();
            RoomRecording r1 = new RoomRecording(room, "eg-1", RecordingType.PARTICIPANT_AUDIO, NOW);

            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(recordingRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(r1));

            List<RoomRecordingResponse> result = recordingService.listRecordings(ROOM_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("활성 녹화 없으면 null 반환")
        void getActiveRecording_noActive_returnsNull() {
            MeetingRoom room = createActiveRoom();
            when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
            when(recordingRepository.findByRoomIdAndStatus(ROOM_ID, RecordingStatus.RECORDING))
                    .thenReturn(List.of());

            RoomRecordingResponse result = recordingService.getActiveRecording(ROOM_ID);

            assertThat(result).isNull();
        }
    }
}
