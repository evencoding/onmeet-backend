package com.onmeet.meeting.service;

import com.onmeet.meeting.dto.MeetingCreateRequest;
import com.onmeet.meeting.entity.Meeting;
import com.onmeet.meeting.entity.MeetingParticipant;
import com.onmeet.meeting.repository.MeetingRepository;
import com.onmeet.meeting.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;

    @Transactional
    public String createMeeting(MeetingCreateRequest dto) {
        // 1. 회의방 엔티티 설정
        Meeting meeting = new Meeting();
        // meeting.setId(...) 제거: 엔티티의 @GeneratedValue가 자동으로 처리합니다.

        meeting.setTitle(dto.getTitle());
        meeting.setDescription(dto.getDescription());
        meeting.setMeetTag(dto.getMeetTag());
        meeting.setTeamId(dto.getTeamId());
        meeting.setHostUserId(dto.getHostId());

        // 날짜와 시간 결합 후 두 필드에 모두 저장
        if (dto.getDate() != null && dto.getTime() != null) {
            LocalDateTime dateTime = LocalDateTime.of(dto.getDate(), dto.getTime());
            meeting.setScheduledAt(dateTime);
            meeting.setStartedAt(dateTime); // 요청하신 대로 시작 시간에도 동일한 값 설정
        }

        // 회의 저장 (이 시점에 DB에 insert 되면서 ID가 생성됩니다)
        Meeting savedMeeting = meetingRepository.save(meeting);

        // 2. 호스트(생성자)를 참여자로 등록
        saveParticipant(savedMeeting, dto.getHostId(),
                MeetingParticipant.UserRole.HOST,
                MeetingParticipant.JoinStatus.JOINED);

        // 3. 초대된 팀원들 등록
        if (dto.getInvitedUserIds() != null) {
            for (String userId : dto.getInvitedUserIds()) {
                saveParticipant(savedMeeting, userId,
                        MeetingParticipant.UserRole.PARTICIPANT,
                        MeetingParticipant.JoinStatus.INVITED);
            }
        }

        // 저장된 회의의 자동 생성된 ID 반환
        return savedMeeting.getId();
    }

    private void saveParticipant(Meeting meeting, String userId,
                                 MeetingParticipant.UserRole role,
                                 MeetingParticipant.JoinStatus status) {
        MeetingParticipant mp = new MeetingParticipant();
        // mp.setId(...) 제거: 엔티티의 @GeneratedValue가 자동으로 처리합니다.

        mp.setMeeting(meeting);
        mp.setUserId(userId);
        mp.setRole(role);
        mp.setJoinStatus(status);

        participantRepository.save(mp);
    }
}