package com.onmeet.auth.entity

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash(value = "refreshToken", timeToLive = 604800) // 7 days
class RefreshToken(
    @Id
    val mobileOrEmail: String,
    val token: String,
    val authority: String
)
