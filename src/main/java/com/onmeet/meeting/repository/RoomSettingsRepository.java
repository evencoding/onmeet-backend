package com.onmeet.meeting.repository;

import com.onmeet.meeting.entity.RoomSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomSettingsRepository extends JpaRepository<RoomSettings, Long> {

    Optional<RoomSettings> findByRoomId(Long roomId);
}
