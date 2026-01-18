package com.onmeet.voice.service;

import com.onmeet.meeting.repository.MeetingRepository;
import com.onmeet.user.repository.UserRepository;
import com.onmeet.voice.dto.*;
import com.onmeet.voice.entity.VoiceSegments;
import com.onmeet.voice.repository.VoiceSegmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceSegmentService {

    private final VoiceSegmentRepository voiceSegmentRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;

    @Transactional
    public void save(String meetingId, VoiceSegmentsSaveRequest request) {
        if (request.items().size() > 2000) {
            throw new IllegalArgumentException("too many items");
        }

        var meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("meeting not found"));

        var entities = request.items().stream().map(it -> {
            var speaker = (it.speakerUserId() == null) ? null :
                    userRepository.findById(it.speakerUserId())
                            .orElseThrow(() -> new IllegalArgumentException("speaker user not found"));

            return new VoiceSegments(
                    meeting,
                    speaker,
                    it.segmentsStartMs(),
                    it.segmentsEndMs(),
                    it.content()
            );
        }).toList();

        voiceSegmentRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public VoiceSegmentsResponse list(String meetingId, Long fromMs, Long toMs, int limit) {
        var pageable = PageRequest.of(0, Math.min(limit, 2000));

        var segments = (fromMs != null && toMs != null)
                ? voiceSegmentRepository.findByMeeting_IdAndSegmentsStartMsBetweenOrderBySegmentsStartMsAsc(
                meetingId, fromMs, toMs, pageable
        )
                : voiceSegmentRepository.findByMeeting_IdOrderBySegmentsStartMsAsc(meetingId, pageable);

        var items = segments.stream().map(s -> new VoiceSegmentResponse(
                s.getId(),
                meetingId,
                s.getSpeakerUser() == null ? null : s.getSpeakerUser().getId(),
                s.getSegmentsStartMs(),
                s.getSegmentsEndMs(),
                s.getContent(),
                s.isFinal(),
                s.getCreatedAt()
        )).toList();

        return new VoiceSegmentsResponse(meetingId, items);
    }
}
