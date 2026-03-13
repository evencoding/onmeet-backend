package com.onmeet.video.meeting.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.VideoErrorCode;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.DataPacketKind;
import com.onmeet.video.infra.livekit.LiveKitClient.TokenGrants;
import com.onmeet.video.infra.livekit.LiveKitProperties;
import com.onmeet.video.meeting.dto.chat.ChatTokenRequest;
import com.onmeet.video.meeting.dto.chat.ChatTokenResponse;
import com.onmeet.video.meeting.dto.chat.DataChannelMessage;
import com.onmeet.video.meeting.dto.chat.SendChatRequest;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.event.chat.ChatMessageEvent;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatIntegrationService {

    private final MeetingRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final LiveKitClient liveKitClient;
    private final LiveKitProperties liveKitProperties;
    private final MeetingEventPublisher eventPublisher;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;
    private final AuthServiceClient authServiceClient;

    public ChatIntegrationService(MeetingRoomRepository roomRepository,
                                  RoomParticipantRepository participantRepository,
                                  LiveKitClient liveKitClient,
                                  LiveKitProperties liveKitProperties,
                                  MeetingEventPublisher eventPublisher,
                                  ClockProvider clockProvider,
                                  ObjectMapper objectMapper,
                                  AuthServiceClient authServiceClient) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.liveKitClient = liveKitClient;
        this.liveKitProperties = liveKitProperties;
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
        this.authServiceClient = authServiceClient;
    }

    @Transactional(readOnly = true)
    public ChatTokenResponse generateChatToken(ChatTokenRequest request) {
        MeetingRoom room = resolveRoom(request);

        if (room.isEnded()) {
            // TODO: [VIDEO][VideoErrorCode.ROOM_ALREADY_ENDED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.ROOM_ALREADY_ENDED);
        }

        String token = liveKitClient.generateToken(
            room.getLivekitRoomName(),
            request.serviceIdentity(),
            "chat-service",
            TokenGrants.forChatService()
        );

        return new ChatTokenResponse(
            token,
            liveKitProperties.getUrl(),
            room.getLivekitRoomName(),
            room.getId()
        );
    }

    @Transactional(readOnly = true)
    public void sendMessage(Long roomId, SendChatRequest request, Long senderId) {
        MeetingRoom room = roomRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(VideoErrorCode.ROOM_NOT_FOUND));

        if (!room.isActive()) {
            // TODO: [VIDEO][VideoErrorCode.ROOM_NOT_ACTIVE] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.ROOM_NOT_ACTIVE);
        }

        boolean isParticipant = participantRepository.existsByRoomIdAndUserIdAndStatusIn(
            roomId, senderId, java.util.List.of(ParticipantStatus.JOINED));
        if (!isParticipant) {
            // TODO: [VIDEO][VideoErrorCode.NOT_PARTICIPANT_CHAT] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.NOT_PARTICIPANT_CHAT);
        }

        String messageType = request.type() != null ? request.type() : DataChannelMessage.TYPE_CHAT;
        String messageId = UUID.randomUUID().toString();

        String senderName;
        try {
            AuthServiceClient.UserInfo userInfo = authServiceClient.getUserInfo(senderId);
            senderName = userInfo != null ? userInfo.name() : "user-" + senderId;
        } catch (Exception e) {
            senderName = "user-" + senderId;
        }

        DataChannelMessage message = new DataChannelMessage(
            messageId,
            messageType,
            senderId,
            senderName,
            request.content(),
            request.replyToMessageId(),
            clockProvider.now()
        );

        byte[] payload = serializeMessage(message);

        if (request.destinationUserId() != null) {
            liveKitClient.publishData(
                room.getLivekitRoomName(),
                payload,
                DataPacketKind.RELIABLE,
                String.valueOf(request.destinationUserId())
            );
        } else {
            liveKitClient.publishData(
                room.getLivekitRoomName(),
                payload,
                DataPacketKind.RELIABLE
            );
        }

        eventPublisher.publishChatMessage(new ChatMessageEvent(
            messageId,
            roomId,
            room.getLivekitRoomName(),
            senderId,
            String.valueOf(senderId),
            messageType,
            request.content(),
            request.replyToMessageId(),
            message.timestamp()
        ));
    }

    @Transactional(readOnly = true)
    public void handleDataReceived(String roomName, String senderIdentity, byte[] data) {
        MeetingRoom room = roomRepository.findByLivekitRoomName(roomName).orElse(null);
        if (room == null) {
            return;
        }

        DataChannelMessage message = deserializeMessage(data);
        if (message == null) {
            return;
        }

        Long senderId = parseSenderId(senderIdentity);

        eventPublisher.publishChatMessage(new ChatMessageEvent(
            message.messageId() != null ? message.messageId() : UUID.randomUUID().toString(),
            room.getId(),
            roomName,
            senderId,
            senderIdentity,
            message.type() != null ? message.type() : DataChannelMessage.TYPE_CHAT,
            message.content(),
            message.replyToMessageId(),
            message.timestamp() != null ? message.timestamp() : clockProvider.now()
        ));
    }

    private MeetingRoom resolveRoom(ChatTokenRequest request) {
        if (request.roomId() != null) {
            return roomRepository.findById(request.roomId())
                .orElseThrow(() -> new BusinessException(VideoErrorCode.ROOM_NOT_FOUND));
        }
        if (request.roomCode() != null) {
            return roomRepository.findByRoomCode(request.roomCode())
                .orElseThrow(() -> new BusinessException(VideoErrorCode.ROOM_NOT_FOUND));
        }
        // TODO: [VIDEO][VideoErrorCode.ROOM_ID_OR_CODE_REQUIRED] 에러메시지 검수 요청
        throw new BusinessException(VideoErrorCode.ROOM_ID_OR_CODE_REQUIRED);
    }

    private byte[] serializeMessage(DataChannelMessage message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            // TODO: [VIDEO][VideoErrorCode.CHAT_SERIALIZE_FAILED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.CHAT_SERIALIZE_FAILED);
        }
    }

    private DataChannelMessage deserializeMessage(byte[] data) {
        try {
            return objectMapper.readValue(data, DataChannelMessage.class);
        } catch (Exception e) {
            try {
                String raw = new String(data, StandardCharsets.UTF_8);
                return new DataChannelMessage(
                    UUID.randomUUID().toString(),
                    DataChannelMessage.TYPE_CHAT,
                    null,
                    null,
                    raw,
                    null,
                    clockProvider.now()
                );
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private Long parseSenderId(String identity) {
        try {
            return Long.parseLong(identity);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
