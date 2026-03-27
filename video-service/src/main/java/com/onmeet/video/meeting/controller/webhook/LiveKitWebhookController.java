package com.onmeet.video.meeting.controller.webhook;

import com.onmeet.video.meeting.service.chat.ChatIntegrationService;
import com.onmeet.video.meeting.service.recording.RoomRecordingService;
import com.onmeet.video.meeting.service.screenshare.ScreenShareService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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
    private final ScreenShareService screenShareService;

    public LiveKitWebhookController(RoomRecordingService recordingService,
                                    ChatIntegrationService chatIntegrationService,
                                    ScreenShareService screenShareService) {
        this.recordingService = recordingService;
        this.chatIntegrationService = chatIntegrationService;
        this.screenShareService = screenShareService;
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
            case "track_unpublished" -> handleTrackUnpublished(payload);
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

    @SuppressWarnings("unchecked")
    private void handleTrackPublished(Map<String, Object> payload) {
        Map<String, Object> track = (Map<String, Object>) payload.get("track");
        Map<String, Object> participant = (Map<String, Object>) payload.get("participant");

        if (track == null || participant == null) {
            log.debug("Track published event received without track or participant info");
            return;
        }

        String source = (String) track.get("source");
        String identity = (String) participant.get("identity");

        if ("SCREEN_SHARE".equals(source) || "SCREEN_SHARE_AUDIO".equals(source)) {
            log.info("Screen share track published: identity={}, source={}", identity, source);
            Long userId = parseUserId(identity);
            Long roomId = resolveRoomId(payload);
            if (userId != null && roomId != null) {
                try {
                    screenShareService.startScreenShare(roomId, userId);
                } catch (Exception e) {
                    log.warn("Failed to update screen share state on track publish: {}", e.getMessage());
                }
            }
        } else if ("MICROPHONE".equals(source)) {
            String trackSid = (String) track.get("sid");
            Long roomId = resolveRoomId(payload);
            if (roomId != null && trackSid != null) {
                try {
                    recordingService.startParticipantTrackEgress(roomId, identity, trackSid);
                } catch (Exception e) {
                    log.warn("Failed to start participant track egress: identity={}, error={}",
                        identity, e.getMessage());
                }
            }
        } else {
            log.debug("Track published event received: source={}", source);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleTrackUnpublished(Map<String, Object> payload) {
        Map<String, Object> track = (Map<String, Object>) payload.get("track");
        Map<String, Object> participant = (Map<String, Object>) payload.get("participant");

        if (track == null || participant == null) {
            return;
        }

        String source = (String) track.get("source");
        String identity = (String) participant.get("identity");

        if ("SCREEN_SHARE".equals(source)) {
            log.info("Screen share track unpublished: identity={}", identity);
            Long userId = parseUserId(identity);
            Long roomId = resolveRoomId(payload);
            if (userId != null && roomId != null) {
                try {
                    screenShareService.stopScreenShare(roomId, userId);
                } catch (Exception e) {
                    log.warn("Failed to update screen share state on track unpublish: {}", e.getMessage());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Long resolveRoomId(Map<String, Object> payload) {
        Map<String, Object> room = (Map<String, Object>) payload.get("room");
        if (room == null) {
            return null;
        }
        String roomName = (String) room.get("name");
        if (roomName == null) {
            return null;
        }
        return screenShareService.findRoomIdByLivekitName(roomName);
    }

    private Long parseUserId(String identity) {
        try {
            return Long.parseLong(identity);
        } catch (NumberFormatException e) {
            return null;
        }
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
            log.info("Egress egressInfo keys: {}", egressInfo.keySet());
            String s3Path = null;
            Long fileSize = null;
            // LiveKit webhook은 snake_case와 camelCase를 혼용할 수 있으므로 둘 다 시도
            List<?> fileResults = (List<?>) egressInfo.get("file_results");
            if (fileResults == null) {
                fileResults = (List<?>) egressInfo.get("fileResults");
            }
            if (fileResults != null && !fileResults.isEmpty()) {
                Map<String, Object> firstFile = (Map<String, Object>) fileResults.get(0);
                s3Path = (String) firstFile.get("filename");
                Number size = (Number) firstFile.get("size");
                fileSize = size != null ? size.longValue() : null;
            }
            // file_results에서 못 찾으면 file 필드도 시도 (단건 결과)
            if (s3Path == null) {
                Map<String, Object> file = (Map<String, Object>) egressInfo.get("file");
                if (file != null) {
                    s3Path = (String) file.get("filename");
                    Number size = (Number) file.get("size");
                    fileSize = size != null ? size.longValue() : null;
                }
            }
            recordingService.handleEgressEnded(egressId, s3Path, fileSize);
            log.info("Egress completed: egressId={}, s3Path={}", egressId, s3Path);
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
