package com.onmeet.auth.controller

import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.*
import com.onmeet.auth.service.AuthService
import com.onmeet.common.security.JwtConstants
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.ExampleObject
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import java.security.Principal
import org.springframework.http.HttpHeaders

import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/")
@Tag(name = "Authentication", description = "인증/인가 및 토큰 관리 API")
class AuthController(
    private val authService: AuthService,
    private val jwtProperties: JwtProperties
) {

    @Operation(summary = "기업(관리자) 회원가입", description = "새로운 기업을 등록하고 관리자 계정을 생성합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "회원가입 성공 (User ID 반환)", content = [Content(schema = Schema(implementation = Long::class))]),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Invalid request format\"}")])]),
        ApiResponse(responseCode = "409", description = "이미 존재하는 이메일/기업", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Email already exists\"}")])])
    ])
    @PostMapping("/register/company")
    fun signupCompany(@RequestBody request: CompanySignupRequest): ResponseEntity<Long> {
        val userId = authService.signupCompany(request)
        return ResponseEntity.ok(userId)
    }

    @Operation(summary = "사원(멤버) 회원가입", description = "초대받은 사원이 기업에 합류하여 계정을 생성합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "회원가입 성공 (User ID 반환)", content = [Content(schema = Schema(implementation = Long::class))]),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Invalid request\"}")])]),
        ApiResponse(responseCode = "404", description = "초대 정보 찾을 수 없음", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Invitation not found\"}")])])
    ])
    @PostMapping("/register/join")
    fun registerEmployee(@RequestBody request: JoinRequest): ResponseEntity<Long> {
        val userId = authService.joinCompany(request)
        return ResponseEntity.ok(userId)
    }

    /**
     * 초대 코드 검증 API
     * 이메일과 코드를 받아 유효한지 검증하고, 초대 정보를 반환합니다.
     */
    @Operation(summary = "초대 코드 검증", description = "이메일과 초대 코드를 검증하여 유효한 초대인지 확인합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "검증 성공 (초대 정보 반환)"),
        ApiResponse(responseCode = "400", description = "유효하지 않은 초대 코드 또는 이메일", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Invalid invitation code\"}")])]),
        ApiResponse(responseCode = "404", description = "초대 정보를 찾을 수 없음", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Invitation not found\"}")])])
    ])
    @GetMapping("/invitations/validate")
    fun validateInvitation(
        @Parameter(description = "초대받은 이메일") @RequestParam email: String,
        @Parameter(description = "초대 코드") @RequestParam code: String
    ): ResponseEntity<InvitationResponse> {
        return ResponseEntity.ok(authService.validateInvitation(email, code))
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하여 Access/Refresh Token을 발급받습니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "로그인 성공 (쿠키에 토큰 설정)"),
        ApiResponse(responseCode = "401", description = "인증 실패 (비밀번호 불일치 등)", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Invalid credentials\"}")])]),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음", content = [Content(examples = [ExampleObject(value = "{\"error\": \"User not found\"}")])])
    ])
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val tokenResponse = authService.login(request)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, createAccessCookie(tokenResponse.accessToken).toString())
            .header(HttpHeaders.SET_COOKIE, createRefreshCookie(tokenResponse.refreshToken ?: "").toString())
            .body(LoginResponse("Login successful"))
    }

    @Operation(summary = "게스트 로그인", description = "게스트 계정으로 로그인하여 토큰을 쿠키로 발급받습니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "로그인 성공 (쿠키에 토큰 설정)"),
        ApiResponse(responseCode = "400", description = "잘못된 요청", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Invalid request\"}")])])
    ])
    @PostMapping("/login/guest")
    fun guestLogin(@RequestBody request: GuestLoginRequest): ResponseEntity<Void> {
        val tokenResponse = authService.guestLogin(request)

        // Guest Refresh Token: 1 day (86400 seconds)
        val guestRefreshMaxAge = 86400L

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, createAccessCookie(tokenResponse.accessToken).toString())
            .header(HttpHeaders.SET_COOKIE, createRefreshCookie(tokenResponse.refreshToken ?: "", guestRefreshMaxAge).toString())
            .build()
    }

    @Operation(summary = "로그아웃", description = "Access Token을 블랙리스트에 추가하고 쿠키를 삭제합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "로그아웃 성공"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Unauthorized\"}")])])
    ])
    @PostMapping("/logout")
    fun logout(
        @CookieValue(name = JwtConstants.ACCESS_TOKEN_COOKIE_NAME, required = false) cookieAccessToken: String?,
        @RequestHeader(JwtConstants.AUTHORIZATION_HEADER, required = false) headerAccessToken: String?,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        // Extract token (cookie priority, then header)
        val accessToken = cookieAccessToken ?: headerAccessToken?.removePrefix(JwtConstants.BEARER_PREFIX)

        authService.logout(accessToken, authentication?.name)

        // Clear tokens from cookies
        val emptyAccessCookie = createAccessCookie("", 0)
        val emptyRefreshCookie = createRefreshCookie("", 0)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, emptyAccessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, emptyRefreshCookie.toString())
            .build()
    }

    @Operation(summary = "토큰 갱신", description = "Refresh Token을 사용하여 새로운 Access Token을 발급받습니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "토큰 갱신 성공", content = [Content(schema = Schema(implementation = TokenResponse::class))]),
        ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Invalid refresh token\"}")])]),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음", content = [Content(examples = [ExampleObject(value = "{\"error\": \"User not found\"}")])])
    ])
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

    @Operation(summary = "내 정보 조회 (테스트용)", description = "현재 인증된 사용자의 정보를 간단히 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = String::class))]),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = [Content(examples = [ExampleObject(value = "{\"error\": \"Unauthorized\"}")])])
    ])
    @GetMapping("/me")
    fun me(principal: Principal): ResponseEntity<String> {
        return ResponseEntity.ok("Hello, ${principal.name}! You are authenticated.")
    }

    @Operation(summary = "헬스 체크", description = "서비스 생존 여부를 확인합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "서비스 정상 동작 중")
    ])
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
