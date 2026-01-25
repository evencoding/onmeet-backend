package com.onmeet.meeting.repository;

import com.onmeet.meeting.entity.Meeting;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    // 오늘 회의 (오늘 00:00 ~ 23:59)
    List<Meeting> findByScheduledAtBetweenOrderByScheduledAtAsc(
            LocalDateTime start,
            LocalDateTime end
    );

    // 이전 회의 (커서 기반)
    List<Meeting> findByScheduledAtBeforeOrderByScheduledAtDesc(
            LocalDateTime cursor,
            Pageable pageable
    );
}
