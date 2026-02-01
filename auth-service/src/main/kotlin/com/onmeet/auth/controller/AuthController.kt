package com.onmeet.auth.controller

import com.onmeet.auth.dto.*
import com.onmeet.auth.service.AuthService
import com.onmeet.common.security.JwtConstants
import java.security.Principal
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
    @Value("\${jwt.cookie.max-age}") private val cookieMaxAge: Long,
    @Value("\${jwt.refresh-cookie.max-age}") private val refreshCookieMaxAge: Long
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

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, createAccessCookie(tokenResponse.accessToken).toString())
            .header(HttpHeaders.SET_COOKIE, createRefreshCookie(tokenResponse.refreshToken ?: "").toString())
            .body(LoginResponse("Login successful"))
    }

    @PostMapping("/guest/login")
    fun guestLogin(@RequestBody request: GuestLoginRequest): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(authService.guestLogin(request))
    }

    @PostMapping("/logout")
    fun logout(principal: Principal?): ResponseEntity<Void> {
        principal?.name?.let { authService.logout(it) }

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, createAccessCookie("", 0).toString())
            .header(HttpHeaders.SET_COOKIE, createRefreshCookie("", 0).toString())
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

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, createAccessCookie(tokenResponse.accessToken).toString())
            .header(HttpHeaders.SET_COOKIE, createRefreshCookie(tokenResponse.refreshToken ?: "").toString())
            .body(tokenResponse)
    }

    @GetMapping("/me")
    fun me(principal: Principal): ResponseEntity<String> {
        return ResponseEntity.ok("Hello, ${principal.name}! You are authenticated.")
    }

    @GetMapping("/check")
    fun check(): ResponseEntity<Void> {
        return ResponseEntity.ok().build()
    }

    private fun createAccessCookie(token: String, maxAge: Long = cookieMaxAge): ResponseCookie {
        return ResponseCookie.from(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, token)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(maxAge)
            .sameSite("Lax")
            .build()
    }

    private fun createRefreshCookie(token: String, maxAge: Long = refreshCookieMaxAge): ResponseCookie {
        return ResponseCookie.from("refreshToken", token)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(maxAge)
            .sameSite("Lax")
            .build()
    }
}
