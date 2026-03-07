package com.onmeet.video.meeting.repository;

import com.onmeet.video.meeting.entity.RoomFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomFavoriteRepository extends JpaRepository<RoomFavorite, Long> {

    Optional<RoomFavorite> findByUserIdAndRoomId(Long userId, Long roomId);

    List<RoomFavorite> findByUserId(Long userId);

    boolean existsByUserIdAndRoomId(Long userId, Long roomId);

    void deleteByUserIdAndRoomId(Long userId, Long roomId);
}
