package com.onmeet.notification.repository;

import com.onmeet.notification.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {
    List<NotificationRecipient> findAllByUserId(Long userId);

    Page<NotificationRecipient> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<NotificationRecipient> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE NotificationRecipient r SET r.readAt = CURRENT_TIMESTAMP WHERE r.userId = :userId AND r.readAt IS NULL")
    int markAllAsReadByUserId(@Param("userId") Long userId);

    void deleteAllByUserId(Long userId);

    @Query("SELECT n.type, COUNT(r) FROM NotificationRecipient r JOIN r.notification n WHERE r.userId = :userId AND r.readAt IS NULL GROUP BY n.type")
    List<Object[]> countUnreadGroupedByType(@Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM notification_recipient WHERE created_at < :cutoffDate LIMIT :batchSize", nativeQuery = true)
    int deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate, @Param("batchSize") int batchSize);
}
