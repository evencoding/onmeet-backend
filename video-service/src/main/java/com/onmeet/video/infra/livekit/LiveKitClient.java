package com.onmeet.video.infra.livekit;

import java.util.List;

public interface LiveKitClient {

    void createRoom(String roomName, int maxParticipants);

    void deleteRoom(String roomName);

    String generateToken(String roomName, String identity, String participantName, TokenGrants grants);

    void removeParticipant(String roomName, String identity);

    void muteParticipantTrack(String roomName, String identity, String trackSid, boolean muted);

    String startRoomCompositeEgress(String roomName, String s3Path);

    String startTrackCompositeEgress(String roomName, String s3Path, int segmentDurationSeconds);

    String startTrackEgress(String roomName, String trackSid, String s3Path);

    List<ParticipantInfo> listParticipants(String roomName);

    void stopEgress(String egressId);

    void publishData(String roomName, byte[] data, DataPacketKind kind);

    void publishData(String roomName, byte[] data, DataPacketKind kind, String destinationIdentity);

    record ParticipantInfo(String identity, String name, List<TrackInfo> tracks) {}

    record TrackInfo(String sid, String source) {}

    record TokenGrants(
        boolean canPublish,
        boolean canSubscribe,
        boolean canPublishData,
        boolean roomAdmin,
        boolean hidden
    ) {
        public TokenGrants(boolean canPublish, boolean canSubscribe, boolean canPublishData, boolean roomAdmin) {
            this(canPublish, canSubscribe, canPublishData, roomAdmin, false);
        }

        public static TokenGrants forHost() {
            return new TokenGrants(true, true, true, true, false);
        }

        public static TokenGrants forParticipant() {
            return new TokenGrants(true, true, true, false, false);
        }

        public static TokenGrants forViewer() {
            return new TokenGrants(false, true, false, false, false);
        }

        public static TokenGrants forChatService() {
            return new TokenGrants(false, true, true, false, true);
        }
    }

    enum DataPacketKind {
        RELIABLE,
        LOSSY
    }
}
