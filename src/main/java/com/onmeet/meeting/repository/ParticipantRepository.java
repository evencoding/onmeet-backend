package com.onmeet.meeting.repository;

import com.onmeet.meeting.entity.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<MeetingParticipant, String> {

}
