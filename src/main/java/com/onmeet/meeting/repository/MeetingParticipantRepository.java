package com.onmeet.meeting.repository;

import com.onmeet.meeting.entity.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {
    boolean existsByMeetingIdAndUserId(java.util.UUID meetingId, java.util.UUID userId);
}
