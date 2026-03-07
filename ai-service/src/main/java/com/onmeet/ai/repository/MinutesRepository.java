package com.onmeet.ai.repository;

import com.onmeet.ai.entity.Minutes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MinutesRepository extends JpaRepository<Minutes, Long> {
    Optional<Minutes> findByRoomId(Long roomId);
}
