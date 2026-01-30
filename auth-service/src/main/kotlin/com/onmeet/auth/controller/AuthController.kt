package com.onmeet.auth.controller

import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.LoginResponse
import com.onmeet.auth.dto.SignupRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.service.AuthService
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
        
        val cookie = ResponseCookie.from("accessToken", tokenResponse.accessToken)
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

    @GetMapping("/me")
    fun me(principal: java.security.Principal): ResponseEntity<String> {
        return ResponseEntity.ok("Hello, ${principal.name}! You are authenticated.")
    }
}
