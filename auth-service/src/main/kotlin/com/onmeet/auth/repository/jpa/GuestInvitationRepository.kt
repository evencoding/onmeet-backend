package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.GuestInvitation
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.Optional

interface GuestInvitationRepository : JpaRepository<GuestInvitation, Long> {
    fun findByUuid(uuid: String): Optional<GuestInvitation>
    fun deleteByExpiresAtBefore(dateTime: LocalDateTime)
}
