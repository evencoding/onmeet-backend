package com.onmeet.notification.repository;

import com.onmeet.notification.entity.Notification;
import com.onmeet.notification.type.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 예약 시간이 지났고 아직 PENDING 상태인 알림을 페이징으로 조회합니다.
     * Pageable로 한 번에 처리할 건수를 제한하여 메모리 폭발을 방지합니다.
     */
    @Query("SELECT n FROM Notification n " +
            "WHERE n.status = :status " +
            "AND n.scheduledAt IS NOT NULL " +
            "AND n.scheduledAt <= :now " +
            "ORDER BY n.scheduledAt ASC")
    List<Notification> findScheduledNotifications(
            @Param("status") NotificationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
