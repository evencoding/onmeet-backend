package com.onmeet.notification.service;

import com.onmeet.notification.dto.NotificationRequestDto;
import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.infra.AuthServiceClient;
import com.onmeet.notification.repository.NotificationRepository;
import com.onmeet.notification.repository.NotificationStreamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * [TDD] receiverName 하드코딩 제거 검증
 *
 * 변경 전: receiverName = "사용자" (하드코딩)
 * 변경 후: authServiceClient.getUserInfo(userId)로 실제 이름 조회 후 사용
 *          auth-service 장애 시 "사용자"로 폴백
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceReceiverNameTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationStreamRepository streamRepository;

    @Mock
    private NotificationSettingService settingService;

    @Mock
    private FcmService fcmService;

    @Mock
    private AuthServiceClient authServiceClient;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("send() 시 receiverName 조회를 위해 authServiceClient.getUserInfo(userId)가 호출된다")
    void send_callsAuthServiceClientForReceiverName() {
        // given
        Long receiverId = 100L;
        Long actorId = 200L;
        NotificationRequestDto dto = NotificationRequestDto.builder()
                .userId(receiverId)
                .type("MEETING_INVITATION")
                .resourceType("MEETING")
                .resourceId("room-1")
                .actorUserId(actorId)
                .build();

        AuthServiceClient.UserInfoResponse receiverInfo =
                new AuthServiceClient.UserInfoResponse(receiverId, "홍길동", "hong@test.com", null, null);

        when(authServiceClient.getUserInfo(receiverId)).thenReturn(receiverInfo);
        when(authServiceClient.getUserName(actorId)).thenReturn("발신자");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settingService.shouldSendNotification(eq(receiverId), any())).thenReturn(true);

        // when
        notificationService.send(dto);

        // then: receiverName 조회를 위해 getUserInfo(receiverId)가 최소 1회 호출되어야 함
        verify(authServiceClient, atLeastOnce()).getUserInfo(receiverId);
    }

    @Test
    @DisplayName("auth-service 장애 시 receiverName이 '사용자'로 폴백되어 예외 없이 처리된다")
    void send_withAuthServiceFailure_fallbacksGracefully() {
        // given
        Long receiverId = 100L;
        Long actorId = 200L;
        NotificationRequestDto dto = NotificationRequestDto.builder()
                .userId(receiverId)
                .type("MEETING_INVITATION")
                .resourceType("MEETING")
                .resourceId("room-1")
                .actorUserId(actorId)
                .build();

        // auth-service 장애 시뮬레이션: getUserInfo 예외 발생 (receiverName 조회 실패)
        when(authServiceClient.getUserInfo(receiverId)).thenThrow(new RuntimeException("auth-service unavailable"));
        when(authServiceClient.getUserName(actorId)).thenReturn("발신자");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settingService.shouldSendNotification(eq(receiverId), any())).thenReturn(true);

        // when & then: 예외 없이 처리되어야 함 (graceful fallback to "사용자")
        assertThatCode(() -> notificationService.send(dto))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("auth-service가 null을 반환하면 receiverName이 '사용자'로 폴백된다")
    void send_withNullUserInfo_fallbacksToDefaultName() {
        // given
        Long receiverId = 100L;
        NotificationRequestDto dto = NotificationRequestDto.builder()
                .userId(receiverId)
                .type("SYSTEM")
                .resourceType("SYSTEM")
                .resourceId("system-1")
                .build();

        when(authServiceClient.getUserInfo(receiverId)).thenReturn(null);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settingService.shouldSendNotification(eq(receiverId), any())).thenReturn(true);

        // when & then: null 반환 시에도 예외 없이 처리
        assertThatCode(() -> notificationService.send(dto))
                .doesNotThrowAnyException();
    }
}
