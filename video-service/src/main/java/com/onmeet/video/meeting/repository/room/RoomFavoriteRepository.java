package com.onmeet.video.meeting.repository.room;

import com.onmeet.video.meeting.entity.room.RoomFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomFavoriteRepository extends JpaRepository<RoomFavorite, Long> {

    Optional<RoomFavorite> findByUserIdAndRoomId(Long userId, Long roomId);

    List<RoomFavorite> findByUserId(Long userId);

    // CHECK [video-담당자]: Page 반환 메서드 추가
    Page<RoomFavorite> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndRoomId(Long userId, Long roomId);

    void deleteByUserIdAndRoomId(Long userId, Long roomId);
}
