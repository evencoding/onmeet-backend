package com.onmeet.notification.repository;

import com.onmeet.notification.entity.NotificationRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    List<NotificationRecipient> findAllByUserId(Long userId);

    /** 내 알림 목록 (최신순, 페이징) */
    Page<NotificationRecipient> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 미읽음 알림 수 */
    long countByUserIdAndReadAtIsNull(Long userId);

    /** 단건 조회 (권한 검증용 — 본인 알림만) */
    Optional<NotificationRecipient> findByIdAndUserId(Long id, Long userId);

    /** 전체 읽음 처리 */
    @Modifying
    @Query("UPDATE NotificationRecipient r SET r.readAt = CURRENT_TIMESTAMP WHERE r.userId = :userId AND r.readAt IS NULL")
    int markAllAsReadByUserId(@Param("userId") Long userId);

    /** 전체 삭제 */
    void deleteAllByUserId(Long userId);
}
