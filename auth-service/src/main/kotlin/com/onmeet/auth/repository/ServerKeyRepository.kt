package com.onmeet.auth.repository

import com.onmeet.auth.entity.ServerKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ServerKeyRepository : JpaRepository<ServerKey, Long> {
    fun findTopByOrderByCreatedAtDesc(): Optional<ServerKey>
}
