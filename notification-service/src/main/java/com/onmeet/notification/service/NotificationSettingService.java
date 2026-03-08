package com.onmeet.notification.service;

import com.onmeet.notification.dto.NotificationSettingDto;
import com.onmeet.notification.entity.NotificationSetting;
import com.onmeet.notification.repository.NotificationSettingRepository;
import com.onmeet.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



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
                        .isMeetingNotification(true)
                        .isMinutesCompletedNotification(true)
                        .isTeamNotification(true)
                        .build());

        setting.update(
                dto.isMeetingNotification(),
                dto.isMinutesCompletedNotification(),
                dto.isTeamNotification());

        settingRepository.save(setting);
    }

    /**
     * 알림 전송 가능 여부를 판단합니다.
     * UI 기획에 맞춰 방해금지 기능을 제거하고 3가지 토글 옵션으로 분기합니다.
     */
    @Transactional(readOnly = true)
    public boolean shouldSendNotification(Long userId, NotificationType type) {
        NotificationSetting setting = settingRepository.findByUserId(userId).orElse(null);

        // 설정이 없으면 기본값 (모두 허용)
        if (setting == null) {
            return true;
        }

        return switch (type) {
            // 회의 알림 토글
            case MEETING_INVITATION, MEETING_STARTED, 
                 MEETING_TODAY, MEETING_REMINDER, 
                 SCHEDULE_CREATED, SCHEDULE_CHANGED, SCHEDULE_CANCELLED ->
                setting.isMeetingNotification();
            
            // 팀 알림 토글
            case TEAM_MEMBER_ADDED, SYSTEM -> 
                setting.isTeamNotification();
            
            // 회의록 완성 알림 토글
            case EVENT, AI_SUMMARY_PROGRESS, AI_SUMMARY_COMPLETED -> 
                setting.isMinutesCompletedNotification();
        };
    }

    private NotificationSettingDto getDefaultSettings() {
        return NotificationSettingDto.builder()
                .isMeetingNotification(true)
                .isMinutesCompletedNotification(true)
                .isTeamNotification(true)
                .build();
    }
}
