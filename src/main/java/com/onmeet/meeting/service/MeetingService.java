package com.onmeet.meeting.service;

import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
import com.onmeet.meeting.dto.MeetingCreateRequest;
import com.onmeet.meeting.dto.MeetingResponse;
import com.onmeet.meeting.entity.Meeting;
import com.onmeet.meeting.repository.MeetingRepository;
import com.onmeet.team.entity.Team;
import com.onmeet.team.repository.TeamRepository;
import com.onmeet.user.entity.User;
import com.onmeet.user.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public MeetingService(MeetingRepository meetingRepository, TeamRepository teamRepository, UserRepository userRepository) {
        this.meetingRepository = meetingRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MeetingResponse create(MeetingCreateRequest request) {
        Team team = teamRepository.findById(request.teamId())
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Team not found"));
        User host = userRepository.findById(request.hostUserId())
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Host user not found"));
        Meeting meeting = new Meeting(team, host, request.title(), request.scheduledAt());
        return toResponse(meetingRepository.save(meeting));
    }

    @Transactional(readOnly = true)
    public MeetingResponse get(String meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Meeting not found"));
        return toResponse(meeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> listByTeam(Long teamId) {
        return meetingRepository.findByTeamId(teamId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private MeetingResponse toResponse(Meeting meeting) {
        return new MeetingResponse(
            meeting.getId(),
            meeting.getTeam().getId(),
            meeting.getHostUser().getId(),
            meeting.getTitle(),
            meeting.getScheduledAt(),
            meeting.getStartedAt(),
            meeting.getEndedAt(),
            meeting.getCreatedAt()
        );
    }
}
