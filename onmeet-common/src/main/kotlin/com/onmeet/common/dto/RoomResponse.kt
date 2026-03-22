package com.onmeet.common.dto

import java.time.Instant

data class RoomResponse(
    val id: Long,
    val roomCode: String,
    val title: String,
    val description: String?,
    val hostUserId: Long,
    val teamId: Long?,
    val locked: Boolean,
    val status: String,
    val type: String,
    val accessScope: String,
    val createdAt: Instant
)
