package com.onmeet.video.meeting.repository;

import com.onmeet.video.meeting.entity.RecordingStatus;
import com.onmeet.video.meeting.entity.RoomRecording;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRecordingRepository extends JpaRepository<RoomRecording, Long> {

    List<RoomRecording> findByRoomId(Long roomId);

    List<RoomRecording> findByRoomIdAndStatus(Long roomId, RecordingStatus status);

    Optional<RoomRecording> findByEgressId(String egressId);
}
