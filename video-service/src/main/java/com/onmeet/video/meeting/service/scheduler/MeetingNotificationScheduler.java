package com.onmeet.video.meeting.service.scheduler;

import com.onmeet.common.dto.NotificationRequestDto;
import com.onmeet.video.meeting.entity.invitation.InvitationStatus;
import com.onmeet.video.meeting.entity.invitation.RoomInvitation;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.room.RoomStatus;
import com.onmeet.video.meeting.entity.room.RoomType;
import com.onmeet.video.meeting.event.NotificationEventPublisher;
import com.onmeet.video.meeting.repository.invitation.RoomInvitationRepository;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingNotificationScheduler {

    private final MeetingRoomRepository meetingRoomRepository;
    private final RoomInvitationRepository roomInvitationRepository;
    private final NotificationEventPublisher notificationEventPublisher;

    /**
     * 매 10분마다 실행 (예: 14:00, 14:10, 14:20 ...)
     * 현재 시간 기준 30분 뒤 ~ 40분 뒤 사이에 시작하는 예약된 회의를 찾아 알림을 발송합니다.
     */
    @Scheduled(cron = "0 0/10 * * * *")
    @Transactional(readOnly = true)
    public void scheduleMeetingReminders() {
        // 기준 시간: 현재 분 단위 절사
        Instant timeSlotStart = Instant.now().truncatedTo(ChronoUnit.MINUTES).plus(30, ChronoUnit.MINUTES);
        Instant timeSlotEnd = timeSlotStart.plus(10, ChronoUnit.MINUTES);

        log.info("Running Meeting Reminders Job for meetings scheduled between {} and {}", timeSlotStart, timeSlotEnd);

        List<MeetingRoom> upcomingMeetings = meetingRoomRepository.findUpcomingMeetings(
            RoomType.SCHEDULED, RoomStatus.WAITING, timeSlotStart, timeSlotEnd);

        for (MeetingRoom room : upcomingMeetings) {
            sendReminderForRoom(room);
        }
    }

    private void sendReminderForRoom(MeetingRoom room) {
        Set<Long> targetUserIds = new HashSet<>();
        // 방장 포함
        targetUserIds.add(room.getHostUserId());

        // 초대를 수락했거나 대기 중인 사용자 조회
        List<RoomInvitation> invitations = roomInvitationRepository.findByRoomId(room.getId());
        for (RoomInvitation invitation : invitations) {
            if (invitation.getStatus() == InvitationStatus.PENDING || invitation.getStatus() == InvitationStatus.ACCEPTED) {
                targetUserIds.add(invitation.getInviteeUserId());
            }
        }

        // 벌크 알림 요청 생성 (userId 대신 userIds 사용)
        NotificationRequestDto request = new NotificationRequestDto(
            null, // userId
            new java.util.ArrayList<>(targetUserIds), // userIds 리스트 전달
            "MEETING_REMINDER",
            "회의 시작 알림",
            "'" + room.getTitle() + "' 회의 시작 30분 전입니다.",
            "/meeting/" + room.getId(),
            "MEETING",
            String.valueOf(room.getId()),
            null // actorUserId
        );
        notificationEventPublisher.publishNotification(request);

        log.info("Sent bulk meeting reminder for room ID: {} to {} users", room.getId(), targetUserIds.size());
    }
}
