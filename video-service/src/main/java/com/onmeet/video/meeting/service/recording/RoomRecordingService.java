package com.onmeet.video.meeting.service.recording;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.VideoErrorCode;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.ParticipantInfo;
import com.onmeet.video.infra.livekit.LiveKitClient.TrackInfo;
import com.onmeet.video.meeting.dto.recording.RoomRecordingResponse;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.entity.recording.RecordingStatus;
import com.onmeet.video.meeting.entity.recording.RecordingType;
import com.onmeet.video.meeting.entity.recording.RoomRecording;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.recording.RoomRecordingRepository;
import com.onmeet.video.meeting.repository.room.RoomSettingsRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomRecordingService {

    private static final Logger log = LoggerFactory.getLogger(RoomRecordingService.class);
    private static final String MICROPHONE_SOURCE = "MICROPHONE";

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
        validateRecordingPreconditions(roomId, room);

        List<ParticipantInfo> participants = liveKitClient.listParticipants(room.getLivekitRoomName());
        Instant now = clockProvider.now();
        List<RoomRecording> recordings = new ArrayList<>();

        for (ParticipantInfo participant : participants) {
            for (TrackInfo track : participant.tracks()) {
                if (!MICROPHONE_SOURCE.equals(track.source())) {
                    continue;
                }
                RoomRecording recording = startTrackEgressForParticipant(
                    room, roomId, participant.identity(), track.sid(), now);
                recordings.add(recording);
            }
        }

        return recordings.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void startParticipantTrackEgress(Long roomId, String participantIdentity, String trackSid) {
        List<RoomRecording> activeRecordings = recordingRepository
            .findByRoomIdAndStatus(roomId, RecordingStatus.RECORDING);
        if (activeRecordings.isEmpty()) {
            return;
        }

        boolean alreadyRecording = recordingRepository
            .findByRoomIdAndTrackSidAndStatus(roomId, trackSid, RecordingStatus.RECORDING)
            .isPresent();
        if (alreadyRecording) {
            return;
        }

        MeetingRoom room = findRoom(roomId);
        Instant now = clockProvider.now();
        startTrackEgressForParticipant(room, roomId, participantIdentity, trackSid, now);
        log.info("Started track egress for late participant: roomId={}, identity={}, trackSid={}",
            roomId, participantIdentity, trackSid);
    }

    @Transactional
    public void stopRecording(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, userId);

        List<RoomRecording> activeRecordings = recordingRepository
            .findByRoomIdAndStatus(roomId, RecordingStatus.RECORDING);

        if (activeRecordings.isEmpty()) {
            // TODO: [VIDEO][VideoErrorCode.ACTIVE_RECORDING_NOT_FOUND] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.ACTIVE_RECORDING_NOT_FOUND);
        }

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
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomRecordingResponse> listRecordings(Long roomId) {
        findRoom(roomId);
        return recordingRepository.findByRoomId(roomId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public RoomRecordingResponse getRecording(Long recordingId) {
        RoomRecording recording = recordingRepository.findById(recordingId)
            .orElseThrow(() -> new BusinessException(VideoErrorCode.RECORDING_NOT_FOUND));
        return toResponse(recording);
    }

    @Transactional(readOnly = true)
    public String getDownloadUrl(Long recordingId) {
        RoomRecording recording = recordingRepository.findById(recordingId)
            .orElseThrow(() -> new BusinessException(VideoErrorCode.RECORDING_NOT_FOUND));

        if (recording.getStatus() != RecordingStatus.COMPLETED) {
            // TODO: [VIDEO][VideoErrorCode.RECORDING_NOT_COMPLETED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.RECORDING_NOT_COMPLETED);
        }

        if (recording.getS3Path() == null) {
            // TODO: [VIDEO][VideoErrorCode.RECORDING_S3_NOT_READY] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.RECORDING_S3_NOT_READY);
        }

        return recording.getS3Path();
    }

    @Transactional
    public void deleteRecording(Long recordingId, Long userId) {
        RoomRecording recording = recordingRepository.findById(recordingId)
            .orElseThrow(() -> new BusinessException(VideoErrorCode.RECORDING_NOT_FOUND));

        MeetingRoom room = recording.getRoom();
        validateHostOrCoHost(room.getId(), room, userId);

        if (recording.isRecording()) {
            liveKitClient.stopEgress(recording.getEgressId());
        }

        recordingRepository.delete(recording);
    }

    @Transactional
    public void handleEgressStarted(String egressId) {
        recordingRepository.findByEgressId(egressId).ifPresent(recording ->
            log.debug("Egress started: egressId={}, participantIdentity={}",
                egressId, recording.getParticipantIdentity())
        );
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

    private RoomRecording startTrackEgressForParticipant(MeetingRoom room, Long roomId,
                                                         String participantIdentity, String trackSid,
                                                         Instant now) {
        String s3Path = buildS3Path(roomId, participantIdentity, trackSid);
        String egressId = liveKitClient.startTrackEgress(room.getLivekitRoomName(), trackSid, s3Path);

        RoomRecording recording = new RoomRecording(
            room, egressId, RecordingType.PARTICIPANT_AUDIO, now, participantIdentity, trackSid);
        return recordingRepository.save(recording);
    }

    private String buildS3Path(Long roomId, String participantIdentity, String trackSid) {
        return "/recordings/" + roomId + "/" + participantIdentity + "/audio_" + trackSid + ".ogg";
    }

    private void validateRecordingPreconditions(Long roomId, MeetingRoom room) {
        if (!room.isActive()) {
            // TODO: [VIDEO][VideoErrorCode.RECORDING_ROOM_NOT_ACTIVE] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.RECORDING_ROOM_NOT_ACTIVE);
        }

        settingsRepository.findByRoomId(roomId).ifPresent(settings -> {
            if (!settings.isRecordingEnabled()) {
                // TODO: [VIDEO][VideoErrorCode.RECORDING_DISABLED] 에러메시지 검수 요청
                throw new BusinessException(VideoErrorCode.RECORDING_DISABLED);
            }
        });

        List<RoomRecording> activeRecordings = recordingRepository
            .findByRoomIdAndStatus(roomId, RecordingStatus.RECORDING);
        if (!activeRecordings.isEmpty()) {
            // TODO: [VIDEO][VideoErrorCode.RECORDING_ALREADY_IN_PROGRESS] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.RECORDING_ALREADY_IN_PROGRESS);
        }
    }

    private MeetingRoom findRoom(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(VideoErrorCode.ROOM_NOT_FOUND));
    }

    private void validateHostOrCoHost(Long roomId, MeetingRoom room, Long userId) {
        if (room.isHost(userId)) {
            return;
        }
        participantRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
            .filter(p -> p.getRole() == ParticipantRole.CO_HOST)
            .orElseThrow(() -> new BusinessException(VideoErrorCode.HOST_OR_COHOST_ONLY));
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
            recording.getParticipantIdentity(),
            recording.getTrackSid(),
            recording.getStartedAt(),
            recording.getEndedAt(),
            recording.getCreatedAt()
        );
    }
}
