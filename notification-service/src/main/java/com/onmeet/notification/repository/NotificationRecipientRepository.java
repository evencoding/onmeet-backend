package com.onmeet.notification.repository;

import com.onmeet.notification.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {
    List<NotificationRecipient> findAllByUserId(Long userId);
}
