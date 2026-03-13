package com.onmeet.auth.controller

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.*
import com.onmeet.auth.security.KeyManager
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.TokenServiceImpl
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
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
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
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: email: 유효한 이메일이 아닙니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_001","status":409,"message":"이미 사용 중인 이메일입니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_003","status":400,"message":"유효하지 않은 초대 코드이거나 이메일이 일치하지 않습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_002","status":404,"message":"초대 코드를 찾을 수 없습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_001","status":409,"message":"이미 사용 중인 이메일입니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_003","status":400,"message":"유효하지 않은 초대 코드이거나 이메일이 일치하지 않습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_002","status":404,"message":"초대 코드를 찾을 수 없습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: 필수 항목이 누락되었습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_004","status":401,"message":"이메일 또는 비밀번호가 일치하지 않습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: 필수 항목이 누락되었습니다","timestamp":1710000000000}"""
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
    @PostMapping("/login/guest")
    fun guestLogin(@RequestBody request: GuestLoginRequest): ResponseEntity<Void> {
        val tokenResponse = authService.guestLogin(request)

        val guestRefreshMaxAge = TokenServiceImpl.GUEST_TOKEN_EXPIRY_SECONDS

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
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_005","status":400,"message":"유효하지 않은 리프레시 토큰입니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_005","status":400,"message":"유효하지 않은 리프레시 토큰입니다","timestamp":1710000000000}"""
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
    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = JwtConstants.REFRESH_TOKEN_COOKIE_NAME, required = false) cookieRefreshToken: String?,
        @RequestBody(required = false) request: RefreshRequest?
    ): ResponseEntity<TokenResponse> {
        // TODO: [AUTH][AuthErrorCode.INVALID_REFRESH_TOKEN] 에러메시지 검수 요청
        val refreshToken = cookieRefreshToken ?: request?.refreshToken
            ?: throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)

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
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_006","status":404,"message":"해당 사용자를 찾을 수 없습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_006","status":404,"message":"해당 사용자를 찾을 수 없습니다","timestamp":1710000000000}"""
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
                    value = """{"code":"AUTH_006","status":404,"message":"해당 사용자를 찾을 수 없습니다","timestamp":1710000000000}"""
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
    @GetMapping("/internal/users/{userId}/company")
    fun getUserCompany(@PathVariable userId: Long): ResponseEntity<CompanyInfoDto> =
        userService.getUserPermissions(userId).let {
            ResponseEntity.ok(CompanyInfoDto(id = it.companyId ?: 0L, name = "Company ${it.companyId}"))
        }

    @Operation(
        summary = "비밀번호 찾기",
        description = """
            이메일 주소를 입력받아 임시 비밀번호를 발급하고 이메일로 전송합니다.

            **주요 기능:**
            - 등록된 이메일 주소로 8자리 임시 비밀번호 발급
            - 임시 비밀번호는 영문 대소문자, 숫자, 특수문자(!@#$%^&*)로 구성
            - 사용자의 isPasswordReset 플래그를 true로 설정
            - Kafka를 통해 email-service로 이메일 발송 요청

            **보안 주의사항:**
            - 임시 비밀번호로 로그인 후 반드시 비밀번호를 변경해야 합니다
            - 비밀번호 변경 시 isPasswordReset 플래그가 자동으로 false로 초기화됩니다

            **이메일 템플릿:**
            - 템플릿명: temporary-password
            - 변수: userName, temporaryPassword
        """
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = """
                임시 비밀번호 발급 성공
                - 8자리 임시 비밀번호가 생성되어 사용자 이메일로 발송되었습니다
                - 사용자의 isPasswordReset 플래그가 true로 설정되었습니다
                - 이메일 발송은 비동기로 처리되며 Kafka를 통해 email-service로 전달됩니다
            """,
            content = [Content(
                mediaType = "application/json",
                examples = [ExampleObject(
                    name = "성공 응답",
                    value = """{"message": "Temporary password has been sent to your email"}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = """
                잘못된 요청 - 유효하지 않은 이메일 형식
                - 이메일 형식이 올바르지 않은 경우
                - 필수 필드(email)가 누락된 경우
            """,
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    name = "이메일 형식 오류",
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: email: 유효한 이메일이 아닙니다","timestamp":1710000000000}"""
                ), ExampleObject(
                    name = "필수 필드 누락",
                    value = """{"code":"COMMON_VALIDATION_FAILED","status":400,"message":"입력값 검증 실패: email: 필수 항목입니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = """
                사용자를 찾을 수 없음
                - 입력한 이메일 주소로 등록된 사용자가 없는 경우
                - 보안상 이유로 이메일 존재 여부는 클라이언트에 명확히 알리지 않을 수 있습니다
            """,
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    name = "사용자 없음",
                    value = """{"code":"AUTH_011","status":404,"message":"해당 이메일로 등록된 사용자가 없습니다","timestamp":1710000000000}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = """
                서버 내부 오류
                - 임시 비밀번호 생성 중 오류 발생
                - 데이터베이스 저장 실패
                - 이메일 발송 요청 실패 (Kafka 연결 오류 등)
            """,
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    name = "서버 오류",
                    value = """{"code":"COMMON_INTERNAL_ERROR","status":500,"message":"서버 내부 오류가 발생했습니다","timestamp":1710000000000}"""
                )]
            )]
        )
    ])
    @PostMapping("/password/find")
    fun findPassword(
        @RequestBody @jakarta.validation.Valid request: FindPasswordRequest
    ): ResponseEntity<Map<String, String>> {
        authService.findPassword(request.email)
        return ResponseEntity.ok(mapOf("message" to "Temporary password has been sent to your email"))
    }
}
