package com.onmeet.auth.controller

import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.GuestInviteRequestDto
import com.onmeet.auth.service.GuestService
import com.onmeet.common.security.JwtConstants
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/v1/guests")
@Tag(name = "Guest", description = "게스트 초대 및 참여 API")
class GuestController(
    private val guestService: GuestService,
    private val jwtProperties: JwtProperties,
    @Value("\${frontend.url:http://localhost:3000}")
    private val frontendUrl: String
) {

    @Operation(summary = "게스트 초대 메일 발송", description = "호스트가 게스트에게 특정 회의실의 초대 메일을 발송합니다.")
    @PostMapping("/invite")
    fun inviteGuest(
        @RequestBody @Valid request: GuestInviteRequestDto,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val inviterEmail = authentication.name
        guestService.inviteGuest(request, inviterEmail)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "게스트 회의 참여", description = "초대 메일의 링크를 통해 회의에 참여하고 게스트 토큰을 발급받습니다.")
    @GetMapping("/join/{uuid}")
    fun joinMeeting(@PathVariable uuid: String): ResponseEntity<Void> {
        val result = guestService.joinMeeting(uuid)

        val accessTokenCookie = ResponseCookie.from(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, result.accessToken)
            .httpOnly(true)
            .secure(jwtProperties.cookie.secure)
            .path("/")
            .maxAge(4 * 60 * 60) // 4 hours
            .sameSite("Lax")
            .build()
            
        val refreshTokenCookie = ResponseCookie.from(JwtConstants.REFRESH_TOKEN_COOKIE_NAME, result.refreshToken)
            .httpOnly(true)
            .secure(jwtProperties.cookie.secure)
            .path("/")
            .maxAge(24 * 60 * 60) // 1 day
            .sameSite("Lax")
            .build()

        val redirectUri = URI.create("$frontendUrl/rooms/${result.roomId}")

        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
            .location(redirectUri)
            .build()
    }
}
