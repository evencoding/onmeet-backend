package com.onmeet.auth.controller

import com.onmeet.auth.dto.*
import com.onmeet.auth.service.AuthService
import com.onmeet.common.security.JwtConstants
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    @Value("\${jwt.cookie.secure}") private val cookieSecure: Boolean,
    @Value("\${jwt.cookie.max-age}") private val cookieMaxAge: Long
) {

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<Long> {
        val userId = authService.signup(request)
        return ResponseEntity.ok(userId)
    }

    @PostMapping("/signup/company")
    fun signupCompany(@RequestBody request: CompanySignupRequest): ResponseEntity<Long> {
        val userId = authService.signupCompany(request)
        return ResponseEntity.ok(userId)
    }

    @PostMapping("/join")
    fun joinCompany(@RequestBody request: JoinRequest): ResponseEntity<Long> {
        val userId = authService.joinCompany(request)
        return ResponseEntity.ok(userId)
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val tokenResponse = authService.login(request)

        val accessCookie = ResponseCookie.from(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, tokenResponse.accessToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(cookieMaxAge)
            .sameSite("Lax")
            .build()

        val refreshCookie = ResponseCookie.from("refreshToken", tokenResponse.refreshToken ?: "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(60 * 60 * 24 * 7) // 7 days
            .sameSite("Lax")
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            .body(LoginResponse("Login successful"))
    }

    @PostMapping("/guest/login")
    fun guestLogin(@RequestBody request: com.onmeet.auth.dto.GuestLoginRequest): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(authService.guestLogin(request))
    }

    @PostMapping("/logout")
    fun logout(principal: java.security.Principal): ResponseEntity<Void> {
        authService.logout(principal.name)

        val accessCookie = ResponseCookie.from(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build()

        val refreshCookie = ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            .build()
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = "refreshToken", required = false) cookieRefreshToken: String?,
        @RequestBody(required = false) request: RefreshRequest?
    ): ResponseEntity<TokenResponse> {
        val refreshToken = cookieRefreshToken ?: request?.refreshToken
            ?: throw IllegalArgumentException("Refresh token is missing")

        val tokenResponse = authService.refresh(refreshToken)

        val accessCookie = ResponseCookie.from(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, tokenResponse.accessToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(cookieMaxAge)
            .sameSite("Lax")
            .build()

        val refreshCookie = ResponseCookie.from("refreshToken", tokenResponse.refreshToken ?: "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(60 * 60 * 24 * 7) // 7 days
            .sameSite("Lax")
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            .body(tokenResponse)
    }

    @GetMapping("/me")
    fun me(principal: java.security.Principal): ResponseEntity<String> {
        return ResponseEntity.ok("Hello, ${principal.name}! You are authenticated.")
    }
}
