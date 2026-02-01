package com.onmeet.auth.controller

import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.*
import com.onmeet.auth.service.AuthService
import com.onmeet.common.security.JwtConstants
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import java.security.Principal
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtProperties: JwtProperties
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
    fun logout(
        authentication: Authentication?,
        response: HttpServletResponse
    ): ResponseEntity<Void> {
        authentication?.name?.let { email ->
            authService.logoutByEmail(email)
        }

        // Clear tokens from cookies
        val accessTokenCookie = Cookie(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, null).apply {
            isHttpOnly = true
            secure = jwtProperties.cookie.secure
            path = "/"
            maxAge = 0
            setAttribute("SameSite", "Lax")
        }
        val refreshTokenCookie = Cookie(JwtConstants.REFRESH_TOKEN_COOKIE_NAME, null).apply {
            isHttpOnly = true
            secure = jwtProperties.cookie.secure
            path = "/"
            maxAge = 0
            setAttribute("SameSite", "Lax")
        }

        response.addCookie(accessTokenCookie)
        response.addCookie(refreshTokenCookie)

        return ResponseEntity.ok().build()
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = JwtConstants.REFRESH_TOKEN_COOKIE_NAME, required = false) cookieRefreshToken: String?,
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

    private fun createRefreshCookie(token: String, maxAge: Long = jwtProperties.refreshCookie.maxAge): ResponseCookie {
        return createHttpOnlyCookie(JwtConstants.REFRESH_TOKEN_COOKIE_NAME, token, maxAge)
    }

    private fun createAccessCookie(token: String, maxAge: Long = jwtProperties.cookie.maxAge): ResponseCookie {
        return createHttpOnlyCookie(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, token, maxAge)
    }

    private fun createHttpOnlyCookie(name: String, value: String, maxAge: Long): ResponseCookie {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(jwtProperties.cookie.secure)
            .path("/")
            .maxAge(maxAge)
            .sameSite("Lax")
            .build()
    }
}
