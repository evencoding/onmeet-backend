package com.onmeet.auth.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "guest_invitations")
@EntityListeners(AuditingEntityListener::class)
class GuestInvitation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 64)
    val uuid: String,

    @Column(nullable = false)
    val guestEmail: String,

    @Column(nullable = false)
    val roomId: String,

    @Column(nullable = false)
    val hostName: String,

    @Column(nullable = false)
    val roomName: String,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(nullable = false)
    var expiresAt: LocalDateTime
) {
    fun requireId(): Long = id ?: throw IllegalStateException("GuestInvitation ID is required but was null")
}
