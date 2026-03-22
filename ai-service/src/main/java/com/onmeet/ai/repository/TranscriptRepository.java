package com.onmeet.ai.repository;

import com.onmeet.ai.entity.Transcript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TranscriptRepository extends JpaRepository<Transcript, Long> {

    Optional<Transcript> findByTranscriptId(String transcriptId);
}
