package com.onmeet.meeting.service;

import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
import com.onmeet.common.util.ClockProvider;
import com.onmeet.infra.external.LiveKitClient;
import com.onmeet.meeting.dto.RoomRecordingResponse;
import com.onmeet.meeting.entity.MeetingRoom;
import com.onmeet.meeting.entity.ParticipantRole;
import com.onmeet.meeting.entity.ParticipantStatus;
import com.onmeet.meeting.entity.RecordingStatus;
import com.onmeet.meeting.entity.RecordingType;
import com.onmeet.meeting.entity.RoomRecording;
import com.onmeet.meeting.repository.MeetingRoomRepository;
import com.onmeet.meeting.repository.RoomParticipantRepository;
import com.onmeet.meeting.repository.RoomRecordingRepository;
import com.onmeet.meeting.repository.RoomSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomRecordingService {

    private final RoomRecordingRepository recordingRepository;
    private final MeetingRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoomSettingsRepository settingsRepository;
    private final LiveKitClient liveKitClient;
    private final ClockProvider clockProvider;

    public RoomRecordingService(RoomRecordingRepository recordingRepository,
                                MeetingRoomRepository roomRepository,
                                RoomParticipantRepository participantRepository,
                                RoomSettingsRepository settingsRepository,
                                LiveKitClient liveKitClient,
                                ClockProvider clockProvider) {
        this.recordingRepository = recordingRepository;
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.settingsRepository = settingsRepository;
        this.liveKitClient = liveKitClient;
        this.clockProvider = clockProvider;
    }

    @Transactional
    public List<RoomRecordingResponse> startRecording(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, userId);

        if (!room.isActive()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Room must be active to start recording");
        }

        settingsRepository.findByRoomId(roomId).ifPresent(settings -> {
            if (!settings.isRecordingEnabled()) {
                throw new BizException(ErrorCode.FORBIDDEN, "Recording is disabled for this room");
            }
        });

        List<RoomRecording> activeRecordings = recordingRepository
            .findByRoomIdAndStatus(roomId, RecordingStatus.RECORDING);
        if (!activeRecordings.isEmpty()) {
            throw new BizException(ErrorCode.CONFLICT, "Recording is already in progress");
        }

        Instant now = clockProvider.now();
        String fullAudioS3Path = "/recordings/" + roomId + "/full_audio.ogg";
        String segmentS3Path = "/segments/" + roomId + "/";

        String fullEgressId = liveKitClient.startRoomCompositeEgress(
            room.getLivekitRoomName(), fullAudioS3Path);
        RoomRecording fullRecording = new RoomRecording(room, fullEgressId, RecordingType.FULL_AUDIO, now);

        String segmentEgressId = liveKitClient.startTrackCompositeEgress(
            room.getLivekitRoomName(), segmentS3Path, 60);
        RoomRecording segmentRecording = new RoomRecording(room, segmentEgressId, RecordingType.SEGMENT, now);

        recordingRepository.save(fullRecording);
        recordingRepository.save(segmentRecording);

        return List.of(toResponse(fullRecording), toResponse(segmentRecording));
    }

    @Transactional
    public void stopRecording(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, userId);

        List<RoomRecording> activeRecordings = recordingRepository
            .findByRoomIdAndStatus(roomId, RecordingStatus.RECORDING);

        if (activeRecordings.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "No active recording found");
        }

        Instant now = clockProvider.now();
        for (RoomRecording recording : activeRecordings) {
            liveKitClient.stopEgress(recording.getEgressId());
            recording.markProcessing();
        }
    }

    @Transactional(readOnly = true)
    public List<RoomRecordingResponse> getRecordingStatus(Long roomId) {
        findRoom(roomId);
        return recordingRepository.findByRoomIdAndStatus(roomId, RecordingStatus.RECORDING).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoomRecordingResponse> listRecordings(Long roomId) {
        findRoom(roomId);
        return recordingRepository.findByRoomId(roomId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoomRecordingResponse getRecording(Long recordingId) {
        RoomRecording recording = recordingRepository.findById(recordingId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Recording not found"));
        return toResponse(recording);
    }

    @Transactional(readOnly = true)
    public String getDownloadUrl(Long recordingId) {
        RoomRecording recording = recordingRepository.findById(recordingId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Recording not found"));

        if (recording.getStatus() != RecordingStatus.COMPLETED) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Recording is not completed yet");
        }

        if (recording.getS3Path() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Recording file not available");
        }

        return recording.getS3Path();
    }

    @Transactional
    public void deleteRecording(Long recordingId, Long userId) {
        RoomRecording recording = recordingRepository.findById(recordingId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Recording not found"));

        MeetingRoom room = recording.getRoom();
        validateHostOrCoHost(room.getId(), room, userId);

        if (recording.isRecording()) {
            liveKitClient.stopEgress(recording.getEgressId());
        }

        recordingRepository.delete(recording);
    }

    @Transactional
    public void handleEgressStarted(String egressId) {
        recordingRepository.findByEgressId(egressId).ifPresent(recording -> {
            // already tracked at startRecording
        });
    }

    @Transactional
    public void handleEgressEnded(String egressId, String s3Path, Long fileSizeBytes) {
        recordingRepository.findByEgressId(egressId).ifPresent(recording -> {
            Instant now = clockProvider.now();
            recording.markCompleted(s3Path, fileSizeBytes, now);
        });
    }

    @Transactional
    public void handleEgressFailed(String egressId, String errorMessage) {
        recordingRepository.findByEgressId(egressId).ifPresent(recording -> {
            Instant now = clockProvider.now();
            recording.markFailed(errorMessage, now);
        });
    }

    private MeetingRoom findRoom(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found"));
    }

    private void validateHostOrCoHost(Long roomId, MeetingRoom room, Long userId) {
        if (room.isHost(userId)) {
            return;
        }
        participantRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
            .filter(p -> p.getRole() == ParticipantRole.CO_HOST)
            .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "Only host or co-host can perform this action"));
    }

    private RoomRecordingResponse toResponse(RoomRecording recording) {
        return new RoomRecordingResponse(
            recording.getId(),
            recording.getRoom().getId(),
            recording.getEgressId(),
            recording.getType(),
            recording.getStatus(),
            recording.getS3Path(),
            recording.getFileSizeBytes(),
            recording.getDurationSeconds(),
            recording.getSegmentIndex(),
            recording.getStartedAt(),
            recording.getEndedAt(),
            recording.getCreatedAt()
        );
    }
}
