package com.onmeet.meeting.controller;

import com.onmeet.meeting.service.ChatIntegrationService;
import com.onmeet.meeting.service.RoomRecordingService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class LiveKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    private final RoomRecordingService recordingService;
    private final ChatIntegrationService chatIntegrationService;

    public LiveKitWebhookController(RoomRecordingService recordingService,
                                    ChatIntegrationService chatIntegrationService) {
        this.recordingService = recordingService;
        this.chatIntegrationService = chatIntegrationService;
    }

    @PostMapping("/livekit")
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
        String event = (String) payload.get("event");

        if (event == null) {
            log.warn("Received webhook without event type");
            return ResponseEntity.ok().build();
        }

        log.info("LiveKit webhook received: event={}", event);

        switch (event) {
            case "room_started" -> handleRoomStarted(payload);
            case "room_finished" -> handleRoomFinished(payload);
            case "participant_joined" -> handleParticipantJoined(payload);
            case "participant_left" -> handleParticipantLeft(payload);
            case "track_published" -> handleTrackPublished(payload);
            case "egress_started" -> handleEgressStarted(payload);
            case "egress_ended" -> handleEgressEnded(payload);
            case "data_received" -> handleDataReceived(payload);
            default -> log.debug("Unhandled webhook event: {}", event);
        }

        return ResponseEntity.ok().build();
    }

    @SuppressWarnings("unchecked")
    private void handleRoomStarted(Map<String, Object> payload) {
        Map<String, Object> room = (Map<String, Object>) payload.get("room");
        if (room != null) {
            log.info("Room started: name={}", room.get("name"));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRoomFinished(Map<String, Object> payload) {
        Map<String, Object> room = (Map<String, Object>) payload.get("room");
        if (room != null) {
            log.info("Room finished: name={}", room.get("name"));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleParticipantJoined(Map<String, Object> payload) {
        Map<String, Object> participant = (Map<String, Object>) payload.get("participant");
        if (participant != null) {
            log.info("Participant joined: identity={}", participant.get("identity"));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleParticipantLeft(Map<String, Object> payload) {
        Map<String, Object> participant = (Map<String, Object>) payload.get("participant");
        if (participant != null) {
            log.info("Participant left: identity={}", participant.get("identity"));
        }
    }

    private void handleTrackPublished(Map<String, Object> payload) {
        log.debug("Track published event received");
    }

    @SuppressWarnings("unchecked")
    private void handleEgressStarted(Map<String, Object> payload) {
        Map<String, Object> egressInfo = (Map<String, Object>) payload.get("egressInfo");
        if (egressInfo != null) {
            String egressId = (String) egressInfo.get("egressId");
            recordingService.handleEgressStarted(egressId);
            log.info("Egress started: egressId={}", egressId);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEgressEnded(Map<String, Object> payload) {
        Map<String, Object> egressInfo = (Map<String, Object>) payload.get("egressInfo");
        if (egressInfo == null) {
            return;
        }

        String egressId = (String) egressInfo.get("egressId");
        String status = (String) egressInfo.get("status");
        String error = (String) egressInfo.get("error");

        if ("EGRESS_COMPLETE".equals(status)) {
            Map<String, Object> fileResults = (Map<String, Object>) egressInfo.get("fileResults");
            String s3Path = null;
            Long fileSize = null;
            if (fileResults != null) {
                s3Path = (String) fileResults.get("filename");
                Number size = (Number) fileResults.get("size");
                fileSize = size != null ? size.longValue() : null;
            }
            recordingService.handleEgressEnded(egressId, s3Path, fileSize);
            log.info("Egress completed: egressId={}", egressId);
        } else {
            recordingService.handleEgressFailed(egressId, error);
            log.warn("Egress failed: egressId={}, error={}", egressId, error);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleDataReceived(Map<String, Object> payload) {
        Map<String, Object> room = (Map<String, Object>) payload.get("room");
        Map<String, Object> participant = (Map<String, Object>) payload.get("participant");

        if (room == null || participant == null) {
            return;
        }

        String roomName = (String) room.get("name");
        String senderIdentity = (String) participant.get("identity");
        String dataBase64 = (String) payload.get("data");

        if (roomName == null || dataBase64 == null) {
            return;
        }

        try {
            byte[] data = Base64.getDecoder().decode(dataBase64);
            chatIntegrationService.handleDataReceived(roomName, senderIdentity, data);
            log.debug("Data received: room={}, sender={}", roomName, senderIdentity);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode data payload: room={}, sender={}", roomName, senderIdentity);
        }
    }
}
