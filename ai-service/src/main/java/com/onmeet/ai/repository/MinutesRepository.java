package com.onmeet.ai.repository;

import com.onmeet.ai.entity.Minutes;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinutesRepository extends JpaRepository<Minutes, String> {
}
