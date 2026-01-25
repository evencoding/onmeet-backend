package com.onmeet.minutes.service;

import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
import com.onmeet.meeting.entity.Meeting;
import com.onmeet.meeting.repository.MeetingRepository;
import com.onmeet.minutes.dto.MinutesCreateRequest;
import com.onmeet.minutes.dto.MinutesResponse;
import com.onmeet.minutes.entity.Minutes;
import com.onmeet.minutes.repository.MinutesRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MinutesService {

    private final MinutesRepository minutesRepository;
    private final MeetingRepository meetingRepository;

    public MinutesService(MinutesRepository minutesRepository, MeetingRepository meetingRepository) {
        this.minutesRepository = minutesRepository;
        this.meetingRepository = meetingRepository;
    }

    @Transactional
    public MinutesResponse create(MinutesCreateRequest request) {
        Meeting meeting = meetingRepository.findById(UUID.fromString(request.meetingId()))
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Meeting not found"));
        Minutes minutes = new Minutes(meeting, request.status(), request.summaryText());
        return toResponse(minutesRepository.save(minutes));
    }

    @Transactional(readOnly = true)
    public MinutesResponse getByMeeting(String meetingId) {
        Minutes minutes = minutesRepository.findByMeetingId(UUID.fromString(meetingId))
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Minutes not found"));
        return toResponse(minutes);
    }

    private MinutesResponse toResponse(Minutes minutes) {
        return new MinutesResponse(
            minutes.getId(),
            minutes.getMeeting().getId(),
            minutes.getStatus(),
            minutes.getSummaryText(),
            minutes.getCreatedAt(),
            minutes.getUpdatedAt()
        );
    }
}
