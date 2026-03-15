package com.onmeet.notification.service;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.NotificationErrorCode;
import com.onmeet.notification.dto.NotificationResponseDto;
import com.onmeet.notification.entity.NotificationRecipient;
import com.onmeet.notification.repository.NotificationRecipientRepository;
import com.onmeet.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRecipientRepository recipientRepository;

    /**
     * 내 알림 목록을 페이징으로 조회합니다.
     */
    public Page<NotificationResponseDto> getMyNotifications(Long userId, Pageable pageable) {
        return recipientRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponseDto::from);
    }

    /**
     * 미읽음 알림 수를 반환합니다.
     */
    public long getUnreadCount(Long userId) {
        return recipientRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /**
     * 단건 알림을 읽음 처리합니다.
     */
    @Transactional
    public void markAsRead(Long recipientId, Long userId) {
        NotificationRecipient recipient = recipientRepository.findByIdAndUserId(recipientId, userId)
                .orElseThrow(() -> {
                    throw new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
                });
        recipient.markAsRead();
    }

    /**
     * 내 모든 알림을 읽음 처리하고, 처리 후 타입별 미읽음 수를 반환합니다.
     *
     * CHECK [frontend-담당자]: markAllAsRead 응답이 Map<String, Integer>로 변경됨.
     * Frontend UnreadCountResponse 타입과 일치하는지 확인 필요.
     *
     * @return 타입별 남은 미읽음 수 (모두 읽음 처리 후에는 빈 Map)
     */
    @Transactional
    public Map<String, Integer> markAllAsRead(Long userId) {
        recipientRepository.markAllAsReadByUserId(userId);
        return recipientRepository.countUnreadGroupedByType(userId)
                .stream()
                .collect(Collectors.toMap(
                        row -> ((NotificationType) row[0]).name(),
                        row -> ((Long) row[1]).intValue()
                ));
    }

    /**
     * 단건 알림을 삭제합니다.
     */
    @Transactional
    public void deleteNotification(Long recipientId, Long userId) {
        NotificationRecipient recipient = recipientRepository.findByIdAndUserId(recipientId, userId)
                .orElseThrow(() -> {
                    throw new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
                });
        recipientRepository.delete(recipient);
    }

    /**
     * 내 모든 알림을 삭제합니다.
     */
    @Transactional
    public void deleteAllNotifications(Long userId) {
        recipientRepository.deleteAllByUserId(userId);
        log.info("Deleted all notifications for userId={}", userId);
    }
}
