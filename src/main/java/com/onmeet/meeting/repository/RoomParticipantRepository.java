package com.onmeet.meeting.repository;

import com.onmeet.meeting.entity.ParticipantStatus;
import com.onmeet.meeting.entity.RoomParticipant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, Long> {

    List<RoomParticipant> findByRoomIdAndStatus(Long roomId, ParticipantStatus status);

    List<RoomParticipant> findByRoomIdAndStatusIn(Long roomId, List<ParticipantStatus> statuses);

    List<RoomParticipant> findByRoomId(Long roomId);

    Optional<RoomParticipant> findByRoomIdAndUserIdAndStatus(Long roomId, Long userId, ParticipantStatus status);

    Optional<RoomParticipant> findByRoomIdAndUserIdAndStatusIn(Long roomId, Long userId, List<ParticipantStatus> statuses);

    @Query("SELECT COUNT(p) FROM RoomParticipant p WHERE p.room.id = :roomId AND p.status = 'JOINED'")
    int countActiveParticipants(@Param("roomId") Long roomId);

    boolean existsByRoomIdAndUserIdAndStatusIn(Long roomId, Long userId, List<ParticipantStatus> statuses);

    @Query("SELECT p FROM RoomParticipant p WHERE p.userId = :userId AND p.status = 'JOINED'")
    Optional<RoomParticipant> findActiveByUserId(@Param("userId") Long userId);
}
