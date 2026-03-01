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
                        .isPushEnabled(true)
                        .isMeetingInviteNotification(true)
                        .isMeetingStartNotification(true)
                        .isMeetingRemindNotification(true)
                        .isMinutesCompletedNotification(true)
                        .isSystemNoticeNotification(true)
                        .isDoNotDisturbEnabled(false)
                        .doNotDisturbStartTime(LocalTime.of(22, 0))
                        .doNotDisturbEndTime(LocalTime.of(8, 0))
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
     * 1. 글로벌 푸시 OFF → 차단
     * 2. 방해금지 시간 → 차단
     * 3. 개별 알림 타입 OFF → 차단
     */
    @Transactional(readOnly = true)
    public boolean shouldSendNotification(Long userId, NotificationType type) {
        NotificationSetting setting = settingRepository.findByUserId(userId).orElse(null);

        // 설정이 없으면 기본값 (모두 허용)
        if (setting == null) {
            return true;
        }

        // 1. 글로벌 푸시 OFF
        if (!setting.isPushEnabled()) {
            return false;
        }

        // 2. 방해금지 시간 체크
        if (setting.isDoNotDisturbEnabled()) {
            LocalTime now = LocalTime.now();
            LocalTime start = setting.getDoNotDisturbStartTime();
            LocalTime end = setting.getDoNotDisturbEndTime();

            if (start != null && end != null) {
                // 방해금지가 자정을 넘기는 경우 (예: 22:00 ~ 08:00)
                if (start.isAfter(end)) {
                    if (now.isAfter(start) || now.isBefore(end)) {
                        return false;
                    }
                } else {
                    if (now.isAfter(start) && now.isBefore(end)) {
                        return false;
                    }
                }
            }
        }

        // 3. 개별 알림 타입별 체크
        return switch (type) {
            case MEETING_CREATED, MEETING_INVITATION, INVITATION_ACCEPTED,
                    INVITATION_DECLINED, INVITATION_CANCELLED ->
                setting.isMeetingInviteNotification();
            case MEETING_STARTED, PARTICIPANT_JOINED_NOTIFY,
                    PARTICIPANT_KICKED, WAITING_ROOM_ADMITTED,
                    WAITING_ROOM_REJECTED ->
                setting.isMeetingStartNotification();
            case MEETING_TODAY, MEETING_REMINDER, SCHEDULE_CREATED,
                    SCHEDULE_CHANGED, SCHEDULE_CANCELLED ->
                setting.isMeetingRemindNotification();
            case TEAM_MEMBER_ADDED -> setting.isSystemNoticeNotification();
            case SYSTEM -> setting.isSystemNoticeNotification();
            case EVENT -> setting.isMinutesCompletedNotification();
        };
    }

    private NotificationSettingDto getDefaultSettings() {
        return NotificationSettingDto.builder()
                .isPushEnabled(true)
                .isMeetingInviteNotification(true)
                .isMeetingStartNotification(true)
                .isMeetingRemindNotification(true)
                .isMinutesCompletedNotification(true)
                .isSystemNoticeNotification(true)
                .isDoNotDisturbEnabled(false)
                .doNotDisturbStartTime(LocalTime.of(22, 0))
                .doNotDisturbEndTime(LocalTime.of(8, 0))
                .build();
    }
}
