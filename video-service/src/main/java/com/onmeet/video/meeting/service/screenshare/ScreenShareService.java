package com.onmeet.video.meeting.service.screenshare;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.VideoErrorCode;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.DataPacketKind;
import com.onmeet.video.meeting.dto.chat.DataChannelMessage;
import com.onmeet.video.meeting.dto.screenshare.ScreenShareResponse;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.entity.participant.RoomParticipant;
import com.onmeet.video.meeting.entity.room.RoomSettings;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.event.screenshare.ScreenShareEvent;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.room.RoomSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreenShareService {

    private final MeetingRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoomSettingsRepository settingsRepository;
    private final LiveKitClient liveKitClient;
    private final MeetingEventPublisher eventPublisher;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;
    private final AuthServiceClient authServiceClient;

    public ScreenShareService(MeetingRoomRepository roomRepository,
                              RoomParticipantRepository participantRepository,
                              RoomSettingsRepository settingsRepository,
                              LiveKitClient liveKitClient,
                              MeetingEventPublisher eventPublisher,
                              ClockProvider clockProvider,
                              ObjectMapper objectMapper,
                              AuthServiceClient authServiceClient) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.settingsRepository = settingsRepository;
        this.liveKitClient = liveKitClient;
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
        this.authServiceClient = authServiceClient;
    }

    @Transactional
    public ScreenShareResponse startScreenShare(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);

        if (!room.isActive()) {
            // TODO: [VIDEO][VideoErrorCode.SCREEN_SHARE_ROOM_NOT_ACTIVE] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.SCREEN_SHARE_ROOM_NOT_ACTIVE);
        }

        RoomSettings settings = settingsRepository.findByRoomId(roomId).orElse(null);
        if (settings != null && !settings.isScreenShareAllowed()) {
            // TODO: [VIDEO][VideoErrorCode.SCREEN_SHARE_NOT_ALLOWED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.SCREEN_SHARE_NOT_ALLOWED);
        }

        RoomParticipant participant = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
            .orElseThrow(() -> new BusinessException(VideoErrorCode.NOT_JOINED_PARTICIPANT));

        if (participant.isScreenSharing()) {
            // TODO: [VIDEO][VideoErrorCode.ALREADY_SHARING] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.ALREADY_SHARING);
        }

        Instant now = clockProvider.now();
        participant.startScreenShare(now);

        publishDataChannelMessage(room, userId, DataChannelMessage.TYPE_SCREEN_SHARE_START);

        eventPublisher.publishScreenShareStarted(
            new ScreenShareEvent("SCREEN_SHARE_STARTED", roomId, userId, now));

        return new ScreenShareResponse(participant.getId(), userId, now);
    }

    @Transactional
    public void stopScreenShare(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);

        RoomParticipant participant = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
            .orElseThrow(() -> new BusinessException(VideoErrorCode.NOT_JOINED_PARTICIPANT));

        if (!participant.isScreenSharing()) {
            // TODO: [VIDEO][VideoErrorCode.NOT_SHARING] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.NOT_SHARING);
        }

        participant.stopScreenShare();

        publishDataChannelMessage(room, userId, DataChannelMessage.TYPE_SCREEN_SHARE_STOP);

        eventPublisher.publishScreenShareStopped(
            new ScreenShareEvent("SCREEN_SHARE_STOPPED", roomId, userId, clockProvider.now()));
    }

    @Transactional
    public void forceStopScreenShare(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        RoomParticipant target = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, ParticipantStatus.JOINED)
            .orElseThrow(() -> new BusinessException(VideoErrorCode.NOT_JOINED_PARTICIPANT));

        if (!target.isScreenSharing()) {
            // TODO: [VIDEO][VideoErrorCode.TARGET_NOT_SHARING] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.TARGET_NOT_SHARING);
        }

        target.stopScreenShare();

        liveKitClient.muteParticipantTrack(
            room.getLivekitRoomName(), String.valueOf(targetUserId), "screen_share", true);

        publishDataChannelMessage(room, targetUserId, DataChannelMessage.TYPE_SCREEN_SHARE_STOP);

        eventPublisher.publishScreenShareStopped(
            new ScreenShareEvent("SCREEN_SHARE_STOPPED", roomId, targetUserId, clockProvider.now()));
    }

    @Transactional(readOnly = true)
    public List<ScreenShareResponse> listActiveScreenShares(Long roomId) {
        findRoom(roomId);

        return participantRepository.findByRoomIdAndScreenSharingTrue(roomId).stream()
            .map(p -> new ScreenShareResponse(p.getId(), p.getUserId(), p.getScreenShareStartedAt()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Long findRoomIdByLivekitName(String livekitRoomName) {
        return roomRepository.findByLivekitRoomName(livekitRoomName)
            .map(MeetingRoom::getId)
            .orElse(null);
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

    private void publishDataChannelMessage(MeetingRoom room, Long userId, String type) {
        String senderName;
        try {
            AuthServiceClient.UserInfo userInfo = authServiceClient.getUserInfo(userId);
            senderName = userInfo != null ? userInfo.name() : "user-" + userId;
        } catch (Exception e) {
            senderName = "user-" + userId;
        }

        DataChannelMessage message = new DataChannelMessage(
            UUID.randomUUID().toString(),
            type,
            userId,
            senderName,
            null,
            null,
            clockProvider.now()
        );

        try {
            byte[] payload = objectMapper.writeValueAsBytes(message);
            liveKitClient.publishData(room.getLivekitRoomName(), payload, DataPacketKind.RELIABLE);
        } catch (JsonProcessingException e) {
            // TODO: [VIDEO][VideoErrorCode.SCREEN_SHARE_SERIALIZE_FAILED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.SCREEN_SHARE_SERIALIZE_FAILED);
        }
    }
}
