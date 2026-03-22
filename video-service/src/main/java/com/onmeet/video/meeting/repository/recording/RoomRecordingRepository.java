package com.onmeet.video.meeting.repository.recording;

import com.onmeet.video.meeting.entity.recording.RecordingStatus;
import com.onmeet.video.meeting.entity.recording.RoomRecording;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRecordingRepository extends JpaRepository<RoomRecording, Long> {

    List<RoomRecording> findByRoomId(Long roomId);

    List<RoomRecording> findByRoomIdAndStatus(Long roomId, RecordingStatus status);

    Optional<RoomRecording> findByEgressId(String egressId);

    List<RoomRecording> findByRoomIdAndParticipantIdentityAndStatus(
        Long roomId, String participantIdentity, RecordingStatus status);

    Optional<RoomRecording> findByRoomIdAndTrackSidAndStatus(
        Long roomId, String trackSid, RecordingStatus status);
}
