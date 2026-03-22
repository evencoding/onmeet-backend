package com.onmeet.notification.service;

import com.onmeet.notification.dto.NotificationSettingDto;
import com.onmeet.notification.entity.NotificationSetting;
import com.onmeet.notification.repository.NotificationSettingRepository;
import com.onmeet.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;



@Service
@RequiredArgsConstructor

public class NotificationSettingService {

    private final NotificationSettingRepository settingRepository;

    @Transactional(readOnly = true)
    public NotificationSettingDto getSettings(Long userId) {
        return settingRepository.findByUserId(userId)
                .map(NotificationSettingDto::from)
                .orElseGet(() -> getDefaultSettings());
    }

    @Transactional
    public void updateSettings(Long userId, NotificationSettingDto dto) {
        NotificationSetting setting = settingRepository.findByUserId(userId)
                .orElseGet(() -> NotificationSetting.builder()
                        .userId(userId)
                        .pushEnabled(true)
                        .meetingInviteNotification(true)
                        .meetingStartNotification(true)
                        .meetingRemindNotification(true)
                        .minutesCompletedNotification(true)
                        .systemNoticeNotification(true)
                        .doNotDisturbEnabled(false)
                        .build());

        setting.update(
                dto.isPushEnabled(),
                dto.isMeetingInviteNotification(),
                dto.isMeetingStartNotification(),
                dto.isMeetingRemindNotification(),
                dto.isMinutesCompletedNotification(),
                dto.isSystemNoticeNotification(),
                dto.isDoNotDisturbEnabled(),
                dto.getDoNotDisturbStartTime(),
                dto.getDoNotDisturbEndTime());

        settingRepository.save(setting);
    }

    /**
     * 알림 전송 가능 여부를 판단합니다.
     * pushEnabled가 false이면 모든 알림 차단.
     * 방해금지 시간대가 설정된 경우 해당 시간대에는 알림 차단.
     */
    @Transactional(readOnly = true)
    public boolean shouldSendNotification(Long userId, NotificationType type) {
        NotificationSetting setting = settingRepository.findByUserId(userId).orElse(null);

        // 설정이 없으면 기본값 (모두 허용)
        if (setting == null) {
            return true;
        }

        // 전체 푸시 OFF
        if (!setting.isPushEnabled()) {
            return false;
        }

        // 방해금지 시간대 체크
        if (setting.isDoNotDisturbEnabled() && isInDoNotDisturbPeriod(setting)) {
            return false;
        }

        return switch (type) {
            // 회의 초대 관련
            case MEETING_CREATED, MEETING_INVITATION,
                 INVITATION_ACCEPTED, INVITATION_DECLINED, INVITATION_CANCELLED ->
                setting.isMeetingInviteNotification();

            // 회의 시작/참가 관련
            case MEETING_STARTED, MEETING_TODAY,
                 WAITING_ROOM_ADMITTED, WAITING_ROOM_REJECTED,
                 PARTICIPANT_KICKED, PARTICIPANT_JOINED_NOTIFY ->
                setting.isMeetingStartNotification();

            // 일정/리마인더 관련
            case SCHEDULE_CREATED, SCHEDULE_CHANGED, SCHEDULE_CANCELLED, MEETING_REMINDER ->
                setting.isMeetingRemindNotification();

            // 회의록/이벤트 알림
            case EVENT, AI_SUMMARY_PROGRESS, AI_SUMMARY_COMPLETED ->
                setting.isMinutesCompletedNotification();

            // 시스템/공지 알림
            case TEAM_MEMBER_ADDED, SYSTEM ->
                setting.isSystemNoticeNotification();
        };
    }

    private boolean isInDoNotDisturbPeriod(NotificationSetting setting) {
        LocalTime start = setting.getDoNotDisturbStartTime();
        LocalTime end = setting.getDoNotDisturbEndTime();
        if (start == null || end == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        // 자정을 넘는 경우 (예: 22:00 ~ 07:00)
        if (start.isAfter(end)) {
            return now.isAfter(start) || now.isBefore(end);
        }
        return now.isAfter(start) && now.isBefore(end);
    }

    private NotificationSettingDto getDefaultSettings() {
        return NotificationSettingDto.builder()
                .pushEnabled(true)
                .meetingInviteNotification(true)
                .meetingStartNotification(true)
                .meetingRemindNotification(true)
                .minutesCompletedNotification(true)
                .systemNoticeNotification(true)
                .doNotDisturbEnabled(false)
                .build();
    }
}
