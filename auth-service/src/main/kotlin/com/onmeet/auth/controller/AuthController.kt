package com.onmeet.auth.controller

import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.dto.JoinRequest
import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.LoginResponse
import com.onmeet.auth.dto.SignupRequest
import com.onmeet.auth.dto.TokenResponse
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

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val tokenResponse = authService.login(request)

        val cookie = ResponseCookie.from(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, tokenResponse.accessToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(cookieMaxAge)
            .sameSite("Lax")
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(LoginResponse())
    }

    @PostMapping("/guest")
    fun guestLogin(@RequestBody request: com.onmeet.auth.dto.GuestLoginRequest): ResponseEntity<TokenResponse> {
        val tokenResponse = authService.guestLogin(request)

        val cookie = ResponseCookie.from("accessToken", tokenResponse.accessToken)
            .httpOnly(true)
            .secure(false) // TODO: Set to true in production
            .path("/")
            .maxAge(14400) // 4 hours
            .build()

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(tokenResponse)
    }

    @PostMapping("/signup/company")
    fun signupCompany(@RequestBody request: CompanySignupRequest): ResponseEntity<Long> {
        val userId = authService.signupCompany(request)
        return ResponseEntity.ok(userId)
    }

    @PostMapping("/signup/join")
    fun joinCompany(@RequestBody request: JoinRequest): ResponseEntity<Long> {
        val userId = authService.joinCompany(request)
        return ResponseEntity.ok(userId)
    }

    @GetMapping("/me")
    fun me(principal: java.security.Principal): ResponseEntity<String> {
        return ResponseEntity.ok("Hello, ${principal.name}! You are authenticated.")
    }
}
