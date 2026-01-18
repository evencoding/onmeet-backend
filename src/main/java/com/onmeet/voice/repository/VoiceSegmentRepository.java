package com.onmeet.voice.repository;

import com.onmeet.voice.entity.VoiceSegments;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoiceSegmentRepository extends JpaRepository<VoiceSegments, String> {

    List<VoiceSegments> findByMeeting_IdOrderBySegmentsStartMsAsc(String meetingId, Pageable pageable);

    List<VoiceSegments> findByMeeting_IdAndSegmentsStartMsBetweenOrderBySegmentsStartMsAsc(
            String meetingId, long fromMs, long toMs, Pageable pageable
    );
}
