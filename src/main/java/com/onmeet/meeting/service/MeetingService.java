package com.onmeet.meeting.service;

import com.onmeet.meeting.dto.MeetingCreateRequest;
import com.onmeet.meeting.dto.MeetingResponse;
import com.onmeet.meeting.entity.Meeting;
import com.onmeet.meeting.entity.MeetingParticipant;
import com.onmeet.meeting.repository.MeetingParticipantRepository;
import com.onmeet.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;

    @Transactional
    public UUID createMeeting(UUID hostUserId, MeetingCreateRequest req) {
        // 1) 날짜/시간 합치기
        LocalDateTime scheduledAt = LocalDateTime.of(req.date(), req.time());

        // 2) Meeting 생성 + 저장
        Meeting meeting = Meeting.builder()
                .teamId(req.teamId())
                .hostUserId(hostUserId)
                .title(req.title())
                .description(req.description())
                .meetTag(req.meetTag())
                .scheduledAt(scheduledAt)
                .build();

        Meeting saved = meetingRepository.save(meeting);

        // 3) host 참가자 자동 등록 (JOINED)
        MeetingParticipant host = MeetingParticipant.create(saved, hostUserId);
        host.join();
        participantRepository.save(host);

        // 4) 초대 이메일은 다음 단계에서 처리

        return saved.getId();
    }

    // 오늘 회의 목록 (시간순)
    public List<MeetingResponse> getTodayMeetings() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        return meetingRepository
                .findByScheduledAtBetweenOrderByScheduledAtAsc(start, end)
                .stream()
                .map(MeetingService::toResponse)
                .toList();
    }

    // 이전 회의 목록 (커서 기반, 최근순)
    public List<MeetingResponse> getPastMeetings(LocalDateTime cursor, int size) {
        LocalDateTime baseTime = (cursor != null) ? cursor : LocalDateTime.now();
        int pageSize = clampSize(size);

        Pageable pageable = PageRequest.of(0, pageSize);

        return meetingRepository
                .findByScheduledAtBeforeOrderByScheduledAtDesc(baseTime, pageable)
                .stream()
                .map(MeetingService::toResponse)
                .toList();
    }

    private int clampSize(int size) {
        if (size <= 0) return 10;
        return Math.min(size, 50);
    }

    private static MeetingResponse toResponse(Meeting m) {
        return new MeetingResponse(
                m.getId(),
                m.getTeamId(),
                m.getHostUserId(),
                m.getTitle(),
                m.getDescription(),
                m.getMeetTag(),
                m.getScheduledAt(),
                m.getStatus(),
                m.isRecording(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }
}
