package com.onmeet.notification.repository;

import com.onmeet.notification.entity.NotificationStream;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationStreamRepository extends JpaRepository<NotificationStream, String> {
    Optional<NotificationStream> findByUserId(Long userId);
}
