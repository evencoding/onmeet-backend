package com.onmeet.ai.repository;

import com.onmeet.ai.entity.TranscriptEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TranscriptEventRepository extends JpaRepository<TranscriptEvent, Long> {

    List<TranscriptEvent> findAllByTranscriptIdOrderBySeqAsc(String transcriptId);

    @Query("SELECT DISTINCT te.participantId FROM TranscriptEvent te WHERE te.transcriptId = :transcriptId AND te.participantId IS NOT NULL")
    List<String> findDistinctParticipantIdsByTranscriptId(String transcriptId);
}
