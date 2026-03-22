package com.onmeet.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onmeet.notification.dto.NotificationSettingDto;
import com.onmeet.notification.entity.NotificationSetting;
import com.onmeet.notification.repository.NotificationSettingRepository;
import com.onmeet.notification.type.NotificationType;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    @Mock
    NotificationSettingRepository settingRepository;

    @InjectMocks
    NotificationSettingService settingService;

    private static final Long USER_ID = 1L;

    private NotificationSetting createSetting(boolean pushEnabled) {
        return NotificationSetting.builder()
                .userId(USER_ID)
                .pushEnabled(pushEnabled)
                .meetingInviteNotification(true)
                .meetingStartNotification(true)
                .meetingRemindNotification(true)
                .minutesCompletedNotification(true)
                .systemNoticeNotification(true)
                .doNotDisturbEnabled(false)
                .build();
    }

    @Nested
    @DisplayName("getSettings")
    class GetSettings {

        @Test
        @DisplayName("설정이 있으면 해당 설정 반환")
        void getSettings_found() {
            NotificationSetting setting = createSetting(true);
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            NotificationSettingDto dto = settingService.getSettings(USER_ID);

            assertThat(dto.isPushEnabled()).isTrue();
            assertThat(dto.isMeetingInviteNotification()).isTrue();
        }

        @Test
        @DisplayName("설정이 없으면 기본값 반환")
        void getSettings_notFound_returnsDefault() {
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            NotificationSettingDto dto = settingService.getSettings(USER_ID);

            assertThat(dto.isPushEnabled()).isTrue();
            assertThat(dto.isMeetingInviteNotification()).isTrue();
            assertThat(dto.isMeetingStartNotification()).isTrue();
            assertThat(dto.isMeetingRemindNotification()).isTrue();
            assertThat(dto.isMinutesCompletedNotification()).isTrue();
            assertThat(dto.isSystemNoticeNotification()).isTrue();
            assertThat(dto.isDoNotDisturbEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("updateSettings")
    class UpdateSettings {

        @Test
        @DisplayName("기존 설정 업데이트")
        void updateSettings_existing() {
            NotificationSetting setting = createSetting(true);
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            NotificationSettingDto dto = NotificationSettingDto.builder()
                    .pushEnabled(false)
                    .meetingInviteNotification(false)
                    .meetingStartNotification(true)
                    .meetingRemindNotification(true)
                    .minutesCompletedNotification(true)
                    .systemNoticeNotification(true)
                    .doNotDisturbEnabled(false)
                    .build();

            settingService.updateSettings(USER_ID, dto);

            assertThat(setting.isPushEnabled()).isFalse();
            assertThat(setting.isMeetingInviteNotification()).isFalse();
            verify(settingRepository).save(setting);
        }

        @Test
        @DisplayName("설정이 없으면 새로 생성")
        void updateSettings_new() {
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            NotificationSettingDto dto = NotificationSettingDto.builder()
                    .pushEnabled(true)
                    .meetingInviteNotification(true)
                    .meetingStartNotification(false)
                    .meetingRemindNotification(true)
                    .minutesCompletedNotification(true)
                    .systemNoticeNotification(true)
                    .doNotDisturbEnabled(false)
                    .build();

            settingService.updateSettings(USER_ID, dto);

            verify(settingRepository).save(any(NotificationSetting.class));
        }
    }

    @Nested
    @DisplayName("shouldSendNotification")
    class ShouldSend {

        @Test
        @DisplayName("설정 없으면 기본적으로 허용")
        void shouldSend_noSetting_returnsTrue() {
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.MEETING_INVITATION))
                    .isTrue();
        }

        @Test
        @DisplayName("pushEnabled가 false이면 모든 알림 차단")
        void shouldSend_pushDisabled_returnsFalse() {
            NotificationSetting setting = createSetting(false);
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.MEETING_INVITATION))
                    .isFalse();
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.SYSTEM))
                    .isFalse();
        }

        @Test
        @DisplayName("회의 초대 알림 비활성화 시 관련 타입 차단")
        void shouldSend_meetingInviteDisabled() {
            NotificationSetting setting = NotificationSetting.builder()
                    .userId(USER_ID)
                    .pushEnabled(true)
                    .meetingInviteNotification(false)
                    .meetingStartNotification(true)
                    .meetingRemindNotification(true)
                    .minutesCompletedNotification(true)
                    .systemNoticeNotification(true)
                    .doNotDisturbEnabled(false)
                    .build();
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.MEETING_INVITATION))
                    .isFalse();
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.INVITATION_ACCEPTED))
                    .isFalse();
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.MEETING_CREATED))
                    .isFalse();
            // 다른 타입은 여전히 허용
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.MEETING_STARTED))
                    .isTrue();
        }

        @Test
        @DisplayName("회의 시작 알림 비활성화 시 관련 타입 차단")
        void shouldSend_meetingStartDisabled() {
            NotificationSetting setting = NotificationSetting.builder()
                    .userId(USER_ID)
                    .pushEnabled(true)
                    .meetingInviteNotification(true)
                    .meetingStartNotification(false)
                    .meetingRemindNotification(true)
                    .minutesCompletedNotification(true)
                    .systemNoticeNotification(true)
                    .doNotDisturbEnabled(false)
                    .build();
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.MEETING_STARTED))
                    .isFalse();
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.WAITING_ROOM_ADMITTED))
                    .isFalse();
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.PARTICIPANT_KICKED))
                    .isFalse();
        }

        @Test
        @DisplayName("리마인더 알림 비활성화 시 관련 타입 차단")
        void shouldSend_remindDisabled() {
            NotificationSetting setting = NotificationSetting.builder()
                    .userId(USER_ID)
                    .pushEnabled(true)
                    .meetingInviteNotification(true)
                    .meetingStartNotification(true)
                    .meetingRemindNotification(false)
                    .minutesCompletedNotification(true)
                    .systemNoticeNotification(true)
                    .doNotDisturbEnabled(false)
                    .build();
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.MEETING_REMINDER))
                    .isFalse();
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.SCHEDULE_CREATED))
                    .isFalse();
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.SCHEDULE_CHANGED))
                    .isFalse();
        }

        @Test
        @DisplayName("회의록/이벤트 알림 비활성화 시 관련 타입 차단")
        void shouldSend_minutesDisabled() {
            NotificationSetting setting = NotificationSetting.builder()
                    .userId(USER_ID)
                    .pushEnabled(true)
                    .meetingInviteNotification(true)
                    .meetingStartNotification(true)
                    .meetingRemindNotification(true)
                    .minutesCompletedNotification(false)
                    .systemNoticeNotification(true)
                    .doNotDisturbEnabled(false)
                    .build();
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.AI_SUMMARY_COMPLETED))
                    .isFalse();
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.EVENT))
                    .isFalse();
        }

        @Test
        @DisplayName("시스템/공지 알림 비활성화 시 관련 타입 차단")
        void shouldSend_systemDisabled() {
            NotificationSetting setting = NotificationSetting.builder()
                    .userId(USER_ID)
                    .pushEnabled(true)
                    .meetingInviteNotification(true)
                    .meetingStartNotification(true)
                    .meetingRemindNotification(true)
                    .minutesCompletedNotification(true)
                    .systemNoticeNotification(false)
                    .doNotDisturbEnabled(false)
                    .build();
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.SYSTEM))
                    .isFalse();
            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.TEAM_MEMBER_ADDED))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Do Not Disturb")
    class DoNotDisturb {

        @Test
        @DisplayName("DND 시간 미설정 시 알림 허용")
        void dnd_noTimeSet_allows() {
            NotificationSetting setting = NotificationSetting.builder()
                    .userId(USER_ID)
                    .pushEnabled(true)
                    .meetingInviteNotification(true)
                    .meetingStartNotification(true)
                    .meetingRemindNotification(true)
                    .minutesCompletedNotification(true)
                    .systemNoticeNotification(true)
                    .doNotDisturbEnabled(true)
                    .doNotDisturbStartTime(null)
                    .doNotDisturbEndTime(null)
                    .build();
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.MEETING_INVITATION))
                    .isTrue();
        }

        @Test
        @DisplayName("DND 비활성화 시 알림 허용")
        void dnd_disabled_allows() {
            NotificationSetting setting = NotificationSetting.builder()
                    .userId(USER_ID)
                    .pushEnabled(true)
                    .meetingInviteNotification(true)
                    .meetingStartNotification(true)
                    .meetingRemindNotification(true)
                    .minutesCompletedNotification(true)
                    .systemNoticeNotification(true)
                    .doNotDisturbEnabled(false)
                    .doNotDisturbStartTime(LocalTime.of(22, 0))
                    .doNotDisturbEndTime(LocalTime.of(7, 0))
                    .build();
            when(settingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));

            assertThat(settingService.shouldSendNotification(USER_ID, NotificationType.MEETING_INVITATION))
                    .isTrue();
        }
    }
}
