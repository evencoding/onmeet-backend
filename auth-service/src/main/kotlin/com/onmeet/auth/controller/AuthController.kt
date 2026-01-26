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
    fun login(@RequestBody request: LoginRequest): ResponseEntity<TokenResponse> {
        // Note: HttpServletResponse might not be easily injected if running on WebFlux unless on Servlet stack.
        // onmeet-backend auth-service is likely MVC (Tomcat/Servlet).
        // Using Spring's ResponseCookie is safer/cleaner.
        println("DEBUG: Login request received for ${request.email}")

        val tokenResponse = authService.login(request)
        println("DEBUG: Login successful, token generated: ${tokenResponse.accessToken.take(10)}...")
        
        val cookie = org.springframework.http.ResponseCookie.from("accessToken", tokenResponse.accessToken)
            .httpOnly(true)
            .secure(false) // Set to true in production (HTTPS)
            .path("/")
            .maxAge(3600) // 1 hour
            // .sameSite("Lax") // Removing SameSite for debugging localhost issues
            .build()
        
        println("DEBUG: Generated Cookie: $cookie")

        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString())
            .body(tokenResponse)
    }

    @GetMapping("/me")
    fun me(principal: java.security.Principal): ResponseEntity<String> {
        return ResponseEntity.ok("Hello, ${principal.name}! You are authenticated.")
    }
}
