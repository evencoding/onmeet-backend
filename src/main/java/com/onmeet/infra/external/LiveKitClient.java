package com.onmeet.infra.external;

public interface LiveKitClient {

    void createRoom(String roomName, int maxParticipants);

    void deleteRoom(String roomName);

    String generateToken(String roomName, String identity, String participantName, TokenGrants grants);

    void removeParticipant(String roomName, String identity);

    void muteParticipantTrack(String roomName, String identity, String trackSid, boolean muted);

    String startRoomCompositeEgress(String roomName, String s3Path);

    String startTrackCompositeEgress(String roomName, String s3Path, int segmentDurationSeconds);

    void stopEgress(String egressId);

    record TokenGrants(
        boolean canPublish,
        boolean canSubscribe,
        boolean canPublishData,
        boolean roomAdmin
    ) {
        public static TokenGrants forHost() {
            return new TokenGrants(true, true, true, true);
        }

        public static TokenGrants forParticipant() {
            return new TokenGrants(true, true, true, false);
        }

        public static TokenGrants forViewer() {
            return new TokenGrants(false, true, false, false);
        }
    }
}
