package com.onmeet.video.meeting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.video.common.exception.BizException;
import com.onmeet.video.common.exception.ErrorCode;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.external.LiveKitClient;
import com.onmeet.video.infra.external.LiveKitClient.DataPacketKind;
import com.onmeet.video.infra.external.LiveKitClient.TokenGrants;
import com.onmeet.video.meeting.config.LiveKitProperties;
import com.onmeet.video.meeting.dto.ChatTokenRequest;
import com.onmeet.video.meeting.dto.ChatTokenResponse;
import com.onmeet.video.meeting.dto.DataChannelMessage;
import com.onmeet.video.meeting.dto.SendChatRequest;
import com.onmeet.video.meeting.entity.MeetingRoom;
import com.onmeet.video.meeting.entity.ParticipantStatus;
import com.onmeet.video.meeting.event.ChatMessageEvent;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.repository.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.RoomParticipantRepository;
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

    public ChatIntegrationService(MeetingRoomRepository roomRepository,
                                  RoomParticipantRepository participantRepository,
                                  LiveKitClient liveKitClient,
                                  LiveKitProperties liveKitProperties,
                                  MeetingEventPublisher eventPublisher,
                                  ClockProvider clockProvider,
                                  ObjectMapper objectMapper) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.liveKitClient = liveKitClient;
        this.liveKitProperties = liveKitProperties;
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ChatTokenResponse generateChatToken(ChatTokenRequest request) {
        MeetingRoom room = resolveRoom(request);

        if (room.isEnded()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Room has already ended");
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
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found"));

        if (!room.isActive()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Room is not active");
        }

        boolean isParticipant = participantRepository.existsByRoomIdAndUserIdAndStatusIn(
            roomId, senderId, java.util.List.of(ParticipantStatus.JOINED));
        if (!isParticipant) {
            throw new BizException(ErrorCode.FORBIDDEN, "Not a participant of this room");
        }

        String messageType = request.type() != null ? request.type() : DataChannelMessage.TYPE_CHAT;
        String messageId = UUID.randomUUID().toString();

        // TODO: [User Service] senderId로 실제 사용자 이름 조회하여 senderName에 전달
        DataChannelMessage message = new DataChannelMessage(
            messageId,
            messageType,
            senderId,
            "user-" + senderId,
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
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found"));
        }
        if (request.roomCode() != null) {
            return roomRepository.findByRoomCode(request.roomCode())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found"));
        }
        throw new BizException(ErrorCode.INVALID_REQUEST, "Either roomId or roomCode must be provided");
    }

    private byte[] serializeMessage(DataChannelMessage message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to serialize chat message");
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
