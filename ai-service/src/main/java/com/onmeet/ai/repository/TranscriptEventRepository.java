package com.onmeet.ai.repository;

import com.onmeet.ai.entity.TranscriptEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptEventRepository extends JpaRepository<TranscriptEvent, Long> {

    List<TranscriptEvent> findAllByTranscriptIdOrderBySeqAsc(String transcriptId);
}
