package com.onmeet.meeting.repository;

import com.onmeet.meeting.entity.MeetingRoom;
import com.onmeet.meeting.entity.RoomAccessScope;
import com.onmeet.meeting.entity.RoomStatus;
import com.onmeet.meeting.entity.RoomType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRoomRepository extends JpaRepository<MeetingRoom, Long> {

    Optional<MeetingRoom> findByRoomCode(String roomCode);

    Optional<MeetingRoom> findByLivekitRoomName(String livekitRoomName);

    boolean existsByRoomCode(String roomCode);

    @Query("SELECT r FROM MeetingRoom r WHERE "
        + "(:status IS NULL OR r.status = :status) AND "
        + "(:type IS NULL OR r.type = :type) AND "
        + "(:accessScope IS NULL OR r.accessScope = :accessScope) AND "
        + "(:hostUserId IS NULL OR r.hostUserId = :hostUserId)")
    Page<MeetingRoom> findAllWithFilters(
        @Param("status") RoomStatus status,
        @Param("type") RoomType type,
        @Param("accessScope") RoomAccessScope accessScope,
        @Param("hostUserId") Long hostUserId,
        Pageable pageable);

    List<MeetingRoom> findByHostUserIdAndStatusNot(Long hostUserId, RoomStatus status);

    List<MeetingRoom> findByHostUserIdAndTypeAndStatusNot(Long hostUserId, RoomType type, RoomStatus status);

    List<MeetingRoom> findByHostUserIdOrderByCreatedAtDesc(Long hostUserId);

    List<MeetingRoom> findByStatusIn(List<RoomStatus> statuses);

    @Query("SELECT COUNT(r) > 0 FROM MeetingRoom r WHERE r.hostUserId = :hostUserId "
        + "AND r.type = :type AND r.status = :status "
        + "AND r.scheduledAt BETWEEN :rangeStart AND :rangeEnd "
        + "AND (:excludeRoomId IS NULL OR r.id != :excludeRoomId)")
    boolean existsConflictingSchedule(
        @Param("hostUserId") Long hostUserId,
        @Param("type") RoomType type,
        @Param("status") RoomStatus status,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd,
        @Param("excludeRoomId") Long excludeRoomId);
}
