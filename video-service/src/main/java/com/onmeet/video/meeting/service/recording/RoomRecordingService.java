package com.onmeet.video.meeting.service.recording;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.VideoErrorCode;
import com.onmeet.common.dto.event.AudioChunkReadyEvent;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.infra.file.FileServiceClient;
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
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.recording.RoomRecordingRepository;
import com.onmeet.video.meeting.repository.room.RoomSettingsRepository;
import com.onmeet.video.meeting.event.room.MeetingEvent;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomRecordingService {

    private static final Logger log = LoggerFactory.getLogger(RoomRecordingService.class);
    private static final String MICROPHONE_SOURCE = "MICROPHONE";
    private final ConcurrentHashMap<Long, MeetingEvent> pendingMeetingEndedEvents = new ConcurrentHashMap<>();

    private final RoomRecordingRepository recordingRepository;
    private final MeetingRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoomSettingsRepository settingsRepository;
    private final LiveKitClient liveKitClient;
    private final ClockProvider clockProvider;
    private final MeetingEventPublisher eventPublisher;
    private final AuthServiceClient authServiceClient;
    private final FileServiceClient fileServiceClient;

    public RoomRecordingService(RoomRecordingRepository recordingRepository,
                                MeetingRoomRepository roomRepository,
                                RoomParticipantRepository participantRepository,
                                RoomSettingsRepository settingsRepository,
                                LiveKitClient liveKitClient,
                                ClockProvider clockProvider,
                                MeetingEventPublisher eventPublisher,
                                AuthServiceClient authServiceClient,
                                FileServiceClient fileServiceClient) {
        this.recordingRepository = recordingRepository;
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.settingsRepository = settingsRepository;
        this.liveKitClient = liveKitClient;
        this.clockProvider = clockProvider;
        this.eventPublisher = eventPublisher;
        this.authServiceClient = authServiceClient;
        this.fileServiceClient = fileServiceClient;
    }

    // CHECK [recording-담당자]: startRecording 반환 타입 List<RoomRecordingResponse> -> void
    @Transactional
    public void startRecording(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, userId);
        validateRecordingPreconditions(roomId, room);

        List<ParticipantInfo> participants = liveKitClient.listParticipants(room.getLivekitRoomName());
        Instant now = clockProvider.now();

        for (ParticipantInfo participant : participants) {
            for (TrackInfo track : participant.tracks()) {
                if (!MICROPHONE_SOURCE.equals(track.source())) {
                    continue;
                }
                try {
                    startTrackEgressForParticipant(room, roomId, participant.identity(), track.sid(), now);
                } catch (Exception e) {
                    log.warn("Failed to start egress for participant: identity={}, trackSid={}, error={}",
                        participant.identity(), track.sid(), e.getMessage());
                }
            }
        }
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
            try {
                liveKitClient.stopEgress(recording.getEgressId());
                recording.markProcessing();
            } catch (Exception e) {
                log.warn("Failed to stop egress: egressId={}, error={}", recording.getEgressId(), e.getMessage());
                recording.markFailed(e.getMessage(), clockProvider.now());
            }
        }
    }

    // CHECK [recording-담당자]: getActiveRecording 추가 - 진행 중인 녹화 단건 반환
    @Transactional(readOnly = true)
    public RoomRecordingResponse getActiveRecording(Long roomId) {
        findRoom(roomId);
        return recordingRepository.findByRoomIdAndStatus(roomId, RecordingStatus.RECORDING).stream()
            .findFirst()
            .map(this::toResponse)
            .orElse(null);
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

        /** TODO: [VIDEO][FILE_SERVICE] fileId가 존재할 경우 파일 서버를 통해 다운로드 URL을 생성하도록 로직 전환 필요 */
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

            // Register the S3 file with file-service to get a fileId
            Long fileId = null;
            try {
                String fileName = s3Path.contains("/")
                        ? s3Path.substring(s3Path.lastIndexOf('/') + 1)
                        : s3Path;
                fileId = fileServiceClient.registerS3File(
                        s3Path, fileName, "audio/ogg",
                        fileSizeBytes != null ? fileSizeBytes : 0L,
                        "recording", "MEETING",
                        String.valueOf(recording.getRoom().getId()));
            } catch (Exception ex) {
                log.error("Failed to register S3 file with file-service: egressId={}, s3Path={}",
                        egressId, s3Path, ex);
            }

            recording.markCompleted(s3Path, fileSizeBytes, now, fileId);

            // Resolve participant name from auth-service
            String participantName = "user-" + recording.getParticipantIdentity();
            try {
                AuthServiceClient.UserInfo userInfo = authServiceClient.getUserInfo(
                        Long.parseLong(recording.getParticipantIdentity()));
                if (userInfo != null) {
                    participantName = userInfo.name();
                }
            } catch (Exception ex) {
                log.warn("Failed to fetch participant name for identity={}",
                        recording.getParticipantIdentity());
            }

            AudioChunkReadyEvent event = AudioChunkReadyEvent.builder()
                    .roomId(recording.getRoom().getId())
                    .participantId(Long.parseLong(recording.getParticipantIdentity()))
                    .participantName(participantName)
                    .segmentIndex(recording.getSegmentIndex() != null ? recording.getSegmentIndex() : 0)
                    .fileId(fileId)
                    .s3Path(s3Path)
                    .startTime(recording.getStartedAt())
                    .endTime(now)
                    .timestamp(now)
                    .build();
            eventPublisher.publishAudioSegmentReady(event);
            checkAndPublishPendingMeetingEnded(recording.getRoom().getId());
        });
    }

    @Transactional
    public void handleEgressFailed(String egressId, String errorMessage) {
        recordingRepository.findByEgressId(egressId).ifPresent(recording -> {
            Instant now = clockProvider.now();
            recording.markFailed(errorMessage, now);
            checkAndPublishPendingMeetingEnded(recording.getRoom().getId());
        });
    }

    public boolean hasActiveRecordings(Long roomId) {
        return !recordingRepository.findByRoomIdAndStatus(roomId, RecordingStatus.RECORDING).isEmpty()
                || !recordingRepository.findByRoomIdAndStatus(roomId, RecordingStatus.PROCESSING).isEmpty();
    }

    public boolean hasAnyRecordings(Long roomId) {
        return !recordingRepository.findByRoomId(roomId).isEmpty();
    }

    public void setPendingMeetingEnded(Long roomId, MeetingEvent event) {
        pendingMeetingEndedEvents.put(roomId, event);
        log.info("Meeting ended event deferred until all egress complete: roomId={}", roomId);
        checkAndPublishPendingMeetingEnded(roomId);
    }

    private void checkAndPublishPendingMeetingEnded(Long roomId) {
        if (!hasActiveRecordings(roomId)) {
            MeetingEvent pending = pendingMeetingEndedEvents.remove(roomId);
            if (pending != null) {
                log.info("All egress completed, scheduling deferred meeting.ended in 15s: roomId={}", roomId);
                java.util.concurrent.CompletableFuture.delayedExecutor(15, java.util.concurrent.TimeUnit.SECONDS)
                        .execute(() -> {
                            eventPublisher.publishMeetingEnded(pending);
                            log.info("Deferred meeting.ended published: roomId={}", roomId);
                        });
            }
        }
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
        return "recordings/" + roomId + "/" + participantIdentity + "/audio_" + trackSid + ".ogg";
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
