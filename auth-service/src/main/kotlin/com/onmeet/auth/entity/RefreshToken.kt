package com.onmeet.auth.entity

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed

@RedisHash(value = "refreshToken", timeToLive = 604800) // 7 days
class RefreshToken(
    @Id
    val mobileOrEmail: String,
    @Indexed
    val token: String,
    val authority: String,
    @org.springframework.data.redis.core.TimeToLive
    var expiration: Long? = null
)
