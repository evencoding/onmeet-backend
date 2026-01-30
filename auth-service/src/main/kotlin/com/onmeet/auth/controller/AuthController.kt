package com.onmeet.auth.controller

import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.SignupRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<Long> {
        val userId = authService.signup(request)
        return ResponseEntity.ok(userId)
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<com.onmeet.auth.dto.LoginResponse> {
        val tokenResponse = authService.login(request)
        // Removed sensitive token logging

        val cookie = org.springframework.http.ResponseCookie.from("accessToken", tokenResponse.accessToken)
            .httpOnly(true)
            .secure(true) // Should be configurable via properties for production
            .path("/")
            .maxAge(3600)
            .sameSite("Lax") // Set Lax for standard cross-site security
            .build()

        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString())
            .body(com.onmeet.auth.dto.LoginResponse())
    }

    @GetMapping("/me")
    fun me(principal: java.security.Principal): ResponseEntity<String> {
        return ResponseEntity.ok("Hello, ${principal.name}! You are authenticated.")
    }
}
