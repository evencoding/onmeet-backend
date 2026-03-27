package com.onmeet.video.meeting.repository.room;

import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.room.RoomAccessScope;
import com.onmeet.video.meeting.entity.room.RoomStatus;
import com.onmeet.video.meeting.entity.room.RoomType;
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

    // CHECK [video-담당자]: Page 반환 메서드 추가
    Page<MeetingRoom> findByHostUserIdAndTypeAndStatusNot(Long hostUserId, RoomType type, RoomStatus status, Pageable pageable);

    List<MeetingRoom> findByHostUserIdOrderByCreatedAtDesc(Long hostUserId);

    Page<MeetingRoom> findByHostUserIdOrderByCreatedAtDesc(Long hostUserId, Pageable pageable);

    @Query("SELECT r FROM MeetingRoom r JOIN RoomTag t ON t.room = r WHERE t.tagName = :tagName")
    Page<MeetingRoom> findByTagName(@Param("tagName") String tagName, Pageable pageable);

    List<MeetingRoom> findByTeamIdInAndAccessScopeAndStatusNot(List<Long> teamIds, RoomAccessScope accessScope, RoomStatus status);

    List<MeetingRoom> findByStatusIn(List<RoomStatus> statuses);

    @Query("SELECT r FROM MeetingRoom r WHERE r.type = :type AND r.status = :status AND r.scheduledAt BETWEEN :start AND :end")
    List<MeetingRoom> findUpcomingMeetings(
        @Param("type") RoomType type,
        @Param("status") RoomStatus status,
        @Param("start") Instant start,
        @Param("end") Instant end);

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
