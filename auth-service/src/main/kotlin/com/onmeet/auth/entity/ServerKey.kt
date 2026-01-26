package com.onmeet.auth.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "server_keys")
class ServerKey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 4096)
    val publicKey: String,

    @Column(nullable = false, length = 4096)
    val privateKey: String,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
