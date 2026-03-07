package com.onmeet.auth.controller

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.*
import com.onmeet.auth.security.KeyManager
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.UserService
import com.onmeet.common.security.JwtConstants
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.ExampleObject
import com.onmeet.common.dto.ErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1")
@Tag(name = "Authentication", description = "인증/인가 및 토큰 관리 API")
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val jwtProperties: JwtProperties,
    private val keyManager: KeyManager
) {

    @Operation(summary = "기업(관리자) 회원가입", description = "새로운 기업을 등록하고 관리자 계정을 생성합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "기업 회원가입 성공 - 생성된 사용자 ID 반환",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = Long::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 필수 필드 누락 또는 유효하지 않은 데이터",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Validation failed: email: must be a valid email", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "409",
            description = "충돌 - 이미 존재하는 이메일 또는 기업",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 409, "message": "Email already exists", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @PostMapping(value = ["/register/company"], consumes = ["multipart/form-data"])
    fun signupCompany(
        @RequestPart("request") request: CompanySignupRequest,
        @RequestPart(value = "profileImage", required = false) profileImage: org.springframework.web.multipart.MultipartFile?
    ): ResponseEntity<Long> =
        ResponseEntity.ok(authService.signupCompany(request, profileImage))

    @Operation(summary = "사원(멤버) 회원가입", description = "초대받은 사원이 기업에 합류하여 계정을 생성합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "사원 회원가입 성공 - 생성된 사용자 ID 반환",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = Long::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 초대 코드 또는 필수 필드 누락",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Invalid invitation code", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "초대를 찾을 수 없음 - 초대가 만료되었거나 존재하지 않음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 404, "message": "Invitation not found", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "409",
            description = "충돌 - 이미 존재하는 이메일",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 409, "message": "Email already exists", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @PostMapping(value = ["/register/join"], consumes = ["multipart/form-data"])
    fun registerEmployee(
        @RequestPart("request") request: JoinRequest,
        @RequestPart(value = "profileImage", required = false) profileImage: org.springframework.web.multipart.MultipartFile?
    ): ResponseEntity<Long> =
        ResponseEntity.ok(authService.joinCompany(request, profileImage))

    @Operation(summary = "초대 코드 검증", description = "이메일과 초대 코드를 검증하여 유효한 초대인지 확인합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "초대 코드 검증 성공 - 초대 정보 반환",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = InvitationResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 유효하지 않은 초대 코드 또는 이메일 불일치",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Invalid invitation code", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "초대를 찾을 수 없음 - 초대가 만료되었거나 존재하지 않음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 404, "message": "Invitation not found", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/invitations/validate")
    fun validateInvitation(
        @RequestParam email: String,
        @RequestParam code: String
    ): ResponseEntity<InvitationResponse> =
        ResponseEntity.ok(authService.validateInvitation(email, code))

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하여 Access/Refresh Token을 발급받습니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "로그인 성공 - 쿠키로 Access/Refresh Token 발급",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = LoginResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 필수 필드 누락",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Invalid argument", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 이메일 또는 비밀번호가 올바르지 않음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 401, "message": "Authentication failed", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
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
        ApiResponse(
            responseCode = "200",
            description = "게스트 로그인 성공 - 쿠키로 Access/Refresh Token 발급 (Refresh Token은 1일 유효)"
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 필수 필드 누락",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Invalid argument", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
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
        ApiResponse(
            responseCode = "200",
            description = "로그아웃 성공 - Access Token이 블랙리스트에 추가되고 쿠키가 삭제됨"
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @PostMapping("/logout")
    fun logout(
        @CookieValue(name = JwtConstants.ACCESS_TOKEN_COOKIE_NAME, required = false) cookieAccessToken: String?,
        @RequestHeader(JwtConstants.AUTHORIZATION_HEADER, required = false) headerAccessToken: String?,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val accessToken = cookieAccessToken ?: headerAccessToken?.removePrefix(JwtConstants.BEARER_PREFIX)
        authService.logout(accessToken, authentication?.name)

        val emptyAccessCookie = createAccessCookie("", 0)
        val emptyRefreshCookie = createRefreshCookie("", 0)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, emptyAccessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, emptyRefreshCookie.toString())
            .build()
    }

    @Operation(summary = "토큰 갱신", description = "Refresh Token을 사용하여 새로운 Access Token을 발급받습니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "토큰 갱신 성공 - 새로운 Access/Refresh Token 발급",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = TokenResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - Refresh Token 누락 또는 유효하지 않은 토큰 형식",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 400, "message": "Refresh token is missing", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "인증 실패 - 만료되었거나 유효하지 않은 Refresh Token",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 401, "message": "Invalid or expired refresh token", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
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

    @Operation(summary = "헬스 체크", description = "서비스 생존 여부를 확인합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "서비스 정상 동작 중"
        )
    ])
    @GetMapping("/check")
    fun check(): ResponseEntity<Void> =
        ResponseEntity.ok().build()

    @Operation(summary = "JWK Set 조회", description = "OAuth2 Resource Server에서 토큰 서명을 검증하기 위한 공개키 목록을 반환합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "JWK Set 반환 성공 - 토큰 검증을 위한 공개키 정보 포함"
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/.well-known/jwks.json")
    fun keys(): Map<String, Any> {
        val rsaKey = RSAKey.Builder(keyManager.publicKey)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(jwtProperties.keyId)
            .build()

        return JWKSet(rsaKey.toPublicJWK()).toJSONObject()
    }

    private fun createRefreshCookie(token: String, maxAge: Long = jwtProperties.refreshCookie.maxAge): ResponseCookie =
        createHttpOnlyCookie(JwtConstants.REFRESH_TOKEN_COOKIE_NAME, token, maxAge)

    private fun createAccessCookie(token: String, maxAge: Long = jwtProperties.cookie.maxAge): ResponseCookie =
        createHttpOnlyCookie(JwtConstants.ACCESS_TOKEN_COOKIE_NAME, token, maxAge)

    private fun createHttpOnlyCookie(name: String, value: String, maxAge: Long): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(jwtProperties.cookie.secure)
            .path("/")
            .maxAge(maxAge)
            .sameSite("Lax")
            .build()
            
    @Operation(summary = "사용자 권한 정보 조회", description = "파일 서비스 등 타 서비스에서 권한 검증을 위해 사용자의 역할, 회사, 팀 정보를 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "권한 정보 조회 성공 - 사용자의 역할, 회사, 팀 정보 반환",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserPermissionResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 404, "message": "User not found", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/internal/users/{userId}/permissions")
    fun getUserPermissions(@PathVariable userId: Long): ResponseEntity<UserPermissionResponse> =
        ResponseEntity.ok(userService.getUserPermissions(userId))

    @Operation(summary = "사용자 팀 정보 조회", description = "타 서비스에서 사용자가 속한 팀 목록을 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "팀 목록 조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = TeamInfoDto::class)
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 404, "message": "User not found", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/internal/users/{userId}/teams")
    fun getUserTeams(@PathVariable userId: Long): ResponseEntity<List<TeamInfoDto>> =
        ResponseEntity.ok(userService.getUserPermissions(userId).teamIds.map { teamId ->
            TeamInfoDto(id = teamId, name = "Team $teamId", color = null)
        })

    @Operation(summary = "사용자 회사 정보 조회", description = "타 서비스에서 사용자의 소속 회사 정보를 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "회사 정보 조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = CompanyInfoDto::class)
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 404, "message": "User not found", "timestamp": 1234567890}"""
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
                    value = """{"status": 500, "message": "Internal server error occurred", "timestamp": 1234567890}"""
                )]
            )]
        )
    ])
    @GetMapping("/internal/users/{userId}/company")
    fun getUserCompany(@PathVariable userId: Long): ResponseEntity<CompanyInfoDto> =
        userService.getUserPermissions(userId).let { 
            ResponseEntity.ok(CompanyInfoDto(id = it.companyId ?: 0L, name = "Company ${it.companyId}"))
        }
}
