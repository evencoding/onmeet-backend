package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.ServerKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ServerKeyRepository : JpaRepository<ServerKey, Long> {
    fun findTopByOrderByCreatedAtDesc(): Optional<ServerKey>
}
