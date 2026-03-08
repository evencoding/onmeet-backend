package com.onmeet.notification.dto;

import com.onmeet.notification.entity.NotificationSetting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;



@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingDto {
    private boolean isMeetingNotification;
    private boolean isMinutesCompletedNotification;
    private boolean isTeamNotification;

    public static NotificationSettingDto from(NotificationSetting setting) {
        return NotificationSettingDto.builder()
                .isMeetingNotification(setting.isMeetingNotification())
                .isMinutesCompletedNotification(setting.isMinutesCompletedNotification())
                .isTeamNotification(setting.isTeamNotification())
                .build();
    }
}
