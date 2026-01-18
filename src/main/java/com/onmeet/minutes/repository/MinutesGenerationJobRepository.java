package com.onmeet.minutes.repository;

import com.onmeet.minutes.entity.MinutesGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MinutesGenerationJobRepository extends JpaRepository<MinutesGenerationJob, String> {
    Optional<MinutesGenerationJob> findFirstByMeeting_IdOrderByRequestedAtDesc(String meetingId);
}