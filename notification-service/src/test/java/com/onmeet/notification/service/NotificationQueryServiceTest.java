package com.onmeet.notification.service;

import com.onmeet.notification.repository.NotificationRecipientRepository;
import com.onmeet.notification.type.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * [TDD] markAllAsRead 응답 스키마 변경 검증
 *
 * 변경 전: { "updatedCount": N }
 * 변경 후: Map<String, Integer> (category → remaining unread count)
 * Frontend UnreadCountResponse와 호환되는 Record<string, number> 형식
 */
@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRecipientRepository recipientRepository;

    @InjectMocks
    private NotificationQueryService notificationQueryService;

    @Test
    @DisplayName("markAllAsRead는 Map<String, Integer> 타입을 반환한다")
    void markAllAsRead_returnsMapType() {
        // given
        Long userId = 1L;
        when(recipientRepository.markAllAsReadByUserId(userId)).thenReturn(5);
        when(recipientRepository.countUnreadGroupedByType(userId)).thenReturn(Collections.emptyList());

        // when
        Map<String, Integer> result = notificationQueryService.markAllAsRead(userId);

        // then: Map 타입으로 반환 (Frontend UnreadCountResponse 스키마 호환)
        // CHECK [frontend-담당자]: markAllAsRead 응답이 Map<String, Integer>로 변경됨.
        // Frontend UnreadCountResponse 타입과 일치하는지 확인 필요.
        assertThat(result)
                .isNotNull()
                .isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("markAllAsRead 후 미읽음이 없으면 빈 Map을 반환한다")
    void markAllAsRead_returnsEmptyMapWhenAllRead() {
        // given
        Long userId = 1L;
        when(recipientRepository.markAllAsReadByUserId(userId)).thenReturn(3);
        when(recipientRepository.countUnreadGroupedByType(userId)).thenReturn(Collections.emptyList());

        // when
        Map<String, Integer> result = notificationQueryService.markAllAsRead(userId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("markAllAsRead 후 타입별 미읽음 수를 Map 키로 반환한다")
    void markAllAsRead_returnsUnreadCountByType() {
        // given
        Long userId = 1L;
        when(recipientRepository.markAllAsReadByUserId(userId)).thenReturn(2);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{NotificationType.SYSTEM, 2L});
        when(recipientRepository.countUnreadGroupedByType(userId)).thenReturn(rows);

        // when
        Map<String, Integer> result = notificationQueryService.markAllAsRead(userId);

        // then
        assertThat(result).containsEntry("SYSTEM", 2);
    }
}
