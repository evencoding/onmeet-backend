package com.onmeet.notification.repository;

import com.onmeet.notification.entity.NotificationRecipient;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {
    List<NotificationRecipient> findByUserId(String userId);
}
