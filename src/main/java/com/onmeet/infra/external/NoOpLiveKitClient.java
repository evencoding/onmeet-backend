package com.onmeet.infra.external;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpLiveKitClient implements LiveKitClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpLiveKitClient.class);

    @Override
    public void createRoom(String roomName, int maxParticipants) {
        log.debug("LiveKit createRoom: name={}, maxParticipants={}", roomName, maxParticipants);
    }

    @Override
    public void deleteRoom(String roomName) {
        log.debug("LiveKit deleteRoom: name={}", roomName);
    }

    @Override
    public String generateToken(String roomName, String identity, String participantName, TokenGrants grants) {
        log.debug("LiveKit generateToken: room={}, identity={}, name={}", roomName, identity, participantName);
        return "lk_stub_token_" + UUID.randomUUID();
    }

    @Override
    public void removeParticipant(String roomName, String identity) {
        log.debug("LiveKit removeParticipant: room={}, identity={}", roomName, identity);
    }

    @Override
    public void muteParticipantTrack(String roomName, String identity, String trackSid, boolean muted) {
        log.debug("LiveKit muteTrack: room={}, identity={}, muted={}", roomName, identity, muted);
    }

    @Override
    public String startRoomCompositeEgress(String roomName, String s3Path) {
        log.debug("LiveKit startRoomCompositeEgress: room={}, s3Path={}", roomName, s3Path);
        return "egress_" + UUID.randomUUID();
    }

    @Override
    public String startTrackCompositeEgress(String roomName, String s3Path, int segmentDurationSeconds) {
        log.debug("LiveKit startTrackCompositeEgress: room={}, s3Path={}, segment={}s", roomName, s3Path, segmentDurationSeconds);
        return "egress_" + UUID.randomUUID();
    }

    @Override
    public void stopEgress(String egressId) {
        log.debug("LiveKit stopEgress: egressId={}", egressId);
    }
}
