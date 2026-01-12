package com.onmeet.minutes.repository;

import com.onmeet.minutes.entity.Minutes;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinutesRepository extends JpaRepository<Minutes, Long> {
    Optional<Minutes> findByMeetingId(String meetingId);
}
