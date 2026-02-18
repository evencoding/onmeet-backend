package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.Invitation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface InvitationRepository : JpaRepository<Invitation, Long> {
    fun findByCode(code: String): Optional<Invitation>
    fun findByEmail(email: String): Optional<Invitation>
}
