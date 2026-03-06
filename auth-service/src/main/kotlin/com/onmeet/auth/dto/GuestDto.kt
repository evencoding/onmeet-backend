package com.onmeet.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class GuestInviteRequestDto(
    @field:NotBlank(message = "Guest email is required")
    @field:Email(message = "Invalid email format")
    val guestEmail: String,

    @field:NotBlank(message = "Room ID is required")
    val roomId: String,

    @field:NotBlank(message = "Room name is required")
    val roomName: String
)

data class GuestJoinResultDto(
    val roomId: String,
    val guestName: String,
    val accessToken: String,
    val refreshToken: String
)
