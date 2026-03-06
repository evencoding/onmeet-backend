package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.WithdrawnUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface WithdrawnUserRepository : JpaRepository<WithdrawnUser, Long> {
    fun deleteAllByWithdrawnAtBefore(dateTime: LocalDateTime)
}
