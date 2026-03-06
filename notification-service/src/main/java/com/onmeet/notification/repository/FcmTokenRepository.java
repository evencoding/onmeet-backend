package com.onmeet.notification.repository;

import com.onmeet.notification.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
    List<FcmToken> findByUserId(Long userId);

    Optional<FcmToken> findByUserIdAndToken(Long userId, String token);

    Optional<FcmToken> findByUserIdAndDeviceId(Long userId, String deviceId);

    void deleteByUserIdAndToken(Long userId, String token);
}
