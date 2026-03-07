package com.onmeet.video.meeting.repository.room;

import com.onmeet.video.meeting.entity.room.RoomSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomSettingsRepository extends JpaRepository<RoomSettings, Long> {

    Optional<RoomSettings> findByRoomId(Long roomId);
}
