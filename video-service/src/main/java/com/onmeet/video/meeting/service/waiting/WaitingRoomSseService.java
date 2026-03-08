package com.onmeet.video.meeting.service.waiting;

import com.onmeet.video.common.exception.BizException;
import com.onmeet.video.common.exception.ErrorCode;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.TokenGrants;
import com.onmeet.video.infra.livekit.LiveKitProperties;
import com.onmeet.video.meeting.dto.participant.RoomParticipantResponse;
import com.onmeet.video.meeting.dto.waiting.WaitingRoomEvent;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.entity.participant.RoomParticipant;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class WaitingRoomSseService {

    private static final Logger log = LoggerFactory.getLogger(WaitingRoomSseService.class);
    private static final long SSE_TIMEOUT = 10 * 60 * 1000L;

    private final Map<Long, Map<Long, SseEmitter>> participantEmitters = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, SseEmitter>> hostEmitters = new ConcurrentHashMap<>();

    private final RoomParticipantRepository participantRepository;
    private final MeetingRoomRepository roomRepository;
    private final LiveKitClient liveKitClient;
    private final LiveKitProperties liveKitProperties;

    public WaitingRoomSseService(RoomParticipantRepository participantRepository,
            MeetingRoomRepository roomRepository,
            LiveKitClient liveKitClient,
            LiveKitProperties liveKitProperties) {
        this.participantRepository = participantRepository;
        this.roomRepository = roomRepository;
        this.liveKitClient = liveKitClient;
        this.liveKitProperties = liveKitProperties;
    }

    public SseEmitter subscribeParticipant(Long roomId, Long userId) {
        MeetingRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found"));

        RoomParticipant participant = participantRepository
                .findByRoomIdAndUserIdAndStatusIn(roomId, userId,
                        java.util.List.of(ParticipantStatus.WAITING, ParticipantStatus.JOINED))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Not a waiting participant"));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        if (participant.getStatus() == ParticipantStatus.JOINED) {
            sendConnectEvent(emitter);
            String token = liveKitClient.generateToken(
                    room.getLivekitRoomName(),
                    String.valueOf(userId),
                    "user-" + userId,
                    TokenGrants.forParticipant());
            sendEvent(emitter, WaitingRoomEvent.admitted(roomId, userId, token,
                    liveKitProperties.getUrl(), room.getLivekitRoomName()));
            return emitter;
        }

        Map<Long, SseEmitter> roomEmitters = participantEmitters.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());

        SseEmitter existing = roomEmitters.get(userId);
        if (existing != null) {
            existing.complete();
        }

        roomEmitters.put(userId, emitter);

        emitter.onCompletion(() -> removeParticipantEmitter(roomId, userId));
        emitter.onTimeout(() -> removeParticipantEmitter(roomId, userId));
        emitter.onError(e -> removeParticipantEmitter(roomId, userId));

        sendConnectEvent(emitter);
        return emitter;
    }

    public SseEmitter subscribeHost(Long roomId, Long userId) {
        MeetingRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found"));

        if (!room.isHost(userId)) {
            participantRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
                    .filter(p -> p.getRole() == ParticipantRole.CO_HOST)
                    .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN,
                            "Only host or co-host can subscribe to waiting room events"));
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        Map<Long, SseEmitter> roomEmitters = hostEmitters.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());

        SseEmitter existing = roomEmitters.get(userId);
        if (existing != null) {
            existing.complete();
        }

        roomEmitters.put(userId, emitter);

        emitter.onCompletion(() -> removeHostEmitter(roomId, userId));
        emitter.onTimeout(() -> removeHostEmitter(roomId, userId));
        emitter.onError(e -> removeHostEmitter(roomId, userId));

        sendConnectEvent(emitter);
        return emitter;
    }

    public void sendAdmittedEvent(Long roomId, Long userId, String token, String livekitUrl, String roomName) {
        Map<Long, SseEmitter> roomEmitters = participantEmitters.get(roomId);
        if (roomEmitters == null) {
            return;
        }
        SseEmitter emitter = roomEmitters.get(userId);
        if (emitter == null) {
            return;
        }
        sendEvent(emitter, WaitingRoomEvent.admitted(roomId, userId, token, livekitUrl, roomName));
        emitter.complete();
        roomEmitters.remove(userId);
    }

    public void sendRejectedEvent(Long roomId, Long userId) {
        Map<Long, SseEmitter> roomEmitters = participantEmitters.get(roomId);
        if (roomEmitters == null) {
            return;
        }
        SseEmitter emitter = roomEmitters.get(userId);
        if (emitter == null) {
            return;
        }
        sendEvent(emitter, WaitingRoomEvent.rejected(roomId, userId));
        emitter.complete();
        roomEmitters.remove(userId);
    }

    public void notifyHostNewWaiter(Long roomId, RoomParticipantResponse participant) {
        Map<Long, SseEmitter> roomEmitters = hostEmitters.get(roomId);
        if (roomEmitters == null) {
            return;
        }
        WaitingRoomEvent event = WaitingRoomEvent.participantWaiting(roomId, participant.userId(), participant);
        for (Map.Entry<Long, SseEmitter> entry : roomEmitters.entrySet()) {
            sendEvent(entry.getValue(), event);
        }
    }

    public void notifyHostWaiterLeft(Long roomId, Long userId) {
        Map<Long, SseEmitter> roomEmitters = hostEmitters.get(roomId);
        if (roomEmitters == null) {
            return;
        }
        WaitingRoomEvent event = WaitingRoomEvent.participantLeftWaiting(roomId, userId);
        for (Map.Entry<Long, SseEmitter> entry : roomEmitters.entrySet()) {
            sendEvent(entry.getValue(), event);
        }
    }

    public void cleanupRoom(Long roomId) {
        Map<Long, SseEmitter> pEmitters = participantEmitters.remove(roomId);
        if (pEmitters != null) {
            pEmitters.values().forEach(SseEmitter::complete);
        }

        Map<Long, SseEmitter> hEmitters = hostEmitters.remove(roomId);
        if (hEmitters != null) {
            hEmitters.values().forEach(SseEmitter::complete);
        }
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        sendHeartbeatToAll(participantEmitters);
        sendHeartbeatToAll(hostEmitters);
    }

    private void sendHeartbeatToAll(Map<Long, Map<Long, SseEmitter>> emitterStore) {
        for (Map.Entry<Long, Map<Long, SseEmitter>> roomEntry : emitterStore.entrySet()) {
            Map<Long, SseEmitter> roomEmitters = roomEntry.getValue();
            for (Map.Entry<Long, SseEmitter> entry : roomEmitters.entrySet()) {
                try {
                    entry.getValue().send(SseEmitter.event().name("heartbeat").data(""));
                } catch (IOException e) {
                    log.debug("Heartbeat failed for room={}, user={}", roomEntry.getKey(), entry.getKey());
                    roomEmitters.remove(entry.getKey());
                }
            }
        }
    }

    private void sendConnectEvent(SseEmitter emitter) {
        sendEvent(emitter, WaitingRoomEvent.connect());
    }

    private void sendEvent(SseEmitter emitter, WaitingRoomEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event));
        } catch (IOException e) {
            log.debug("Failed to send SSE event: {}", event.type());
        }
    }

    private void removeParticipantEmitter(Long roomId, Long userId) {
        Map<Long, SseEmitter> roomEmitters = participantEmitters.get(roomId);
        if (roomEmitters != null) {
            roomEmitters.remove(userId);
            if (roomEmitters.isEmpty()) {
                participantEmitters.remove(roomId);
            }
        }
    }

    private void removeHostEmitter(Long roomId, Long userId) {
        Map<Long, SseEmitter> roomEmitters = hostEmitters.get(roomId);
        if (roomEmitters != null) {
            roomEmitters.remove(userId);
            if (roomEmitters.isEmpty()) {
                hostEmitters.remove(roomId);
            }
        }
    }
}
