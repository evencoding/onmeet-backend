package com.onmeet.auth.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "invitations")
@EntityListeners(AuditingEntityListener::class)
class Invitation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false)
    var code: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: User.Role = User.Role.USER,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    var company: Company,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var expiresAt: LocalDateTime
) {
    fun requireId(): Long = id ?: throw IllegalStateException("Invitation ID is required but was null")
}
