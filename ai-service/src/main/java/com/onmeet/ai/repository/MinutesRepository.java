package com.onmeet.ai.repository;

import com.onmeet.ai.entity.Minutes;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MinutesRepository extends JpaRepository<Minutes, Long> {
    Optional<Minutes> findByRoomId(Long roomId);

    @Query("SELECT m FROM Minutes m WHERE m.description LIKE %:keyword% OR m.keywords LIKE %:keyword% OR m.decisions LIKE %:keyword% OR m.actionItems LIKE %:keyword%")
    List<Minutes> searchByKeyword(@Param("keyword") String keyword);
}
