package com.onmeet.meeting.repository;

import com.onmeet.meeting.entity.MeetingRoom;
import com.onmeet.meeting.entity.RoomStatus;
import com.onmeet.meeting.entity.RoomType;
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
        + "(:hostUserId IS NULL OR r.hostUserId = :hostUserId)")
    Page<MeetingRoom> findAllWithFilters(
        @Param("status") RoomStatus status,
        @Param("type") RoomType type,
        @Param("hostUserId") Long hostUserId,
        Pageable pageable);

    List<MeetingRoom> findByHostUserIdAndStatusNot(Long hostUserId, RoomStatus status);

    List<MeetingRoom> findByHostUserIdAndTypeAndStatusNot(Long hostUserId, RoomType type, RoomStatus status);

    List<MeetingRoom> findByHostUserIdOrderByCreatedAtDesc(Long hostUserId);

    List<MeetingRoom> findByStatusIn(List<RoomStatus> statuses);
}
