package com.onmeet.auth.entity

import com.onmeet.auth.util.PrivacyEncryptor
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "withdrawn_users")
@EntityListeners(AuditingEntityListener::class)
class WithdrawnUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val originalUserId: Long,

    @Convert(converter = PrivacyEncryptor::class)
    @Column(nullable = false)
    val email: String,

    @Convert(converter = PrivacyEncryptor::class)
    @Column(nullable = false)
    val name: String,

    @Column(length = 500)
    val reason: String? = null,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var withdrawnAt: LocalDateTime? = null
)
