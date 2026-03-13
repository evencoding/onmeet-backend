package com.onmeet.auth.controller

import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.GuestInviteRequestDto
import com.onmeet.auth.service.GuestService
import com.onmeet.common.security.JwtConstants
import com.onmeet.common.dto.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "게스트 초대 메일 발송 성공 - 응답 본문 없음"
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 로그인이 필요합니다",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_004","status":401,"message":"인증에 실패했습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "403",
            description = "권한 없음 - 해당 회의실의 호스트만 초대 가능",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_043","status":403,"message":"해당 회의실의 호스트만 게스트를 초대할 수 있습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "초대자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"AUTH_042","status":404,"message":"초대자 정보를 찾을 수 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
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
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "302",
            description = "회의 참여 성공 - 게스트 토큰이 쿠키로 설정되고 회의실로 리다이렉트됩니다"
        ),
        ApiResponse(
            responseCode = "400",
            description = "유효하지 않거나 만료된 초대 링크",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    name = "링크 무효",
                    value = """{"code":"AUTH_044","status":400,"message":"유효하지 않거나 존재하지 않는 게스트 초대 링크입니다","timestamp":1710000000000}"""
                ), ExampleObject(
                    name = "링크 만료",
                    value = """{"code":"AUTH_045","status":400,"message":"게스트 초대 링크가 만료되었습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @GetMapping("/join/{uuid}")
    fun joinMeeting(@PathVariable uuid: String): ResponseEntity<Void> {
        val result = guestService.joinMeeting(uuid)

        // [Fix] HttpHeaders.add()를 사용해 두 Set-Cookie 헤더가 모두 전송되도록 수정
        // (기존 .header() 체인 방식은 동일 헤더명을 덮어씌우는 버그 존재)
        val responseHeaders = HttpHeaders()

        val accessTokenCookie = ResponseCookie.from(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, result.accessToken)
            .httpOnly(true)
            .secure(jwtProperties.cookie.secure)
            .path("/")
            .maxAge(jwtProperties.cookie.maxAge)
            .sameSite("Lax")
            .build()
        responseHeaders.add(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())

        // refreshToken이 존재하는 경우에만 쿠키 설정
        result.refreshToken?.let { refreshToken ->
            val refreshTokenCookie = ResponseCookie.from(JwtConstants.REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(jwtProperties.cookie.secure)
                .path("/")
                .maxAge(jwtProperties.refreshCookie.maxAge)
                .sameSite("Lax")
                .build()
            responseHeaders.add(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
        }

        // [Fix] roomId가 GuestService에서 이미 형식 검증되었으므로 안전하게 사용 가능
        val redirectUri = URI.create("$frontendUrl/rooms/${result.roomId}")

        return ResponseEntity.status(HttpStatus.FOUND)
            .headers(responseHeaders)
            .location(redirectUri)
            .build()
    }
}
