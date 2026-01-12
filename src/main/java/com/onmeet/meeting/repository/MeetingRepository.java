package com.onmeet.meeting.repository;

import com.onmeet.meeting.entity.Meeting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, String> {
    List<Meeting> findByTeamId(Long teamId);
}
