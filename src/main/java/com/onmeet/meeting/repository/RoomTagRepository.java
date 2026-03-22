package com.onmeet.meeting.repository;

import com.onmeet.meeting.entity.RoomTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomTagRepository extends JpaRepository<RoomTag, Long> {

    Optional<RoomTag> findByRoomIdAndTagName(Long roomId, String tagName);

    List<RoomTag> findByRoomId(Long roomId);

    @Query("SELECT DISTINCT t.room.id FROM RoomTag t WHERE t.tagName = :tagName")
    List<Long> findRoomIdsByTagName(@Param("tagName") String tagName);

    void deleteByRoomIdAndTagName(Long roomId, String tagName);
}
