package com.onmeet.common.exception.errorcode

import com.onmeet.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/**
 * gateway-service 에러 코드 정의.
 *
 * 참고: gateway-service는 WebFlux 기반이라 BaseGlobalExceptionHandler(서블릿)를 사용하지 않음.
 * 이 enum은 에러 코드 일관성을 위해 onmeet-common에 정의하되,
 * 실제 핸들링은 gateway-service의 WebExceptionHandler에서 수행한다.
 */
enum class GatewayErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {

    // === JWT/인증 ===
    TOKEN_MISSING("GATEWAY_001", "인증 토큰이 존재하지 않습니다", HttpStatus.UNAUTHORIZED),
    TOKEN_SIGNATURE_INVALID("GATEWAY_002", "인증 토큰의 서명이 유효하지 않습니다", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("GATEWAY_003", "인증 토큰이 만료되었습니다", HttpStatus.UNAUTHORIZED),
    TOKEN_MALFORMED("GATEWAY_004", "인증 토큰 형식이 올바르지 않습니다", HttpStatus.UNAUTHORIZED),
    JWKS_UNAVAILABLE("GATEWAY_005", "인증 서버에 연결할 수 없습니다", HttpStatus.SERVICE_UNAVAILABLE),

    // === 접근 제어 ===
    AUTHENTICATION_REQUIRED("GATEWAY_006", "인증이 필요한 리소스입니다", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("GATEWAY_007", "해당 리소스에 대한 접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    USER_ID_CLAIM_MISSING("GATEWAY_008", "JWT 토큰에 사용자 ID 정보가 없습니다", HttpStatus.UNAUTHORIZED),
    ROLE_CLAIM_MISSING("GATEWAY_009", "JWT 토큰에 권한 정보가 없습니다", HttpStatus.FORBIDDEN),

    // === 설정 ===
    SECRET_NOT_CONFIGURED("GATEWAY_010", "게이트웨이 시크릿 설정이 누락되었습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === Rate Limiting ===
    RATE_LIMIT_EXCEEDED("GATEWAY_011", "요청 한도를 초과하였습니다. 잠시 후 다시 시도해주세요", HttpStatus.TOO_MANY_REQUESTS),
    RATE_LIMITER_UNAVAILABLE("GATEWAY_012", "속도 제한 서버에 연결할 수 없습니다", HttpStatus.SERVICE_UNAVAILABLE),

    // === 라우팅 ===
    IP_UNKNOWN("GATEWAY_013", "요청자의 IP 주소를 확인할 수 없습니다", HttpStatus.BAD_REQUEST),
    SERVICE_UNAVAILABLE("GATEWAY_014", "요청한 서비스에 연결할 수 없습니다", HttpStatus.SERVICE_UNAVAILABLE),
    SERVICE_TIMEOUT("GATEWAY_015", "서비스 응답 시간이 초과되었습니다", HttpStatus.GATEWAY_TIMEOUT),
    ROUTE_NOT_FOUND("GATEWAY_016", "요청한 경로를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    WEBSOCKET_HANDSHAKE_FAILED("GATEWAY_017", "WebSocket 연결 협상에 실패하였습니다", HttpStatus.BAD_REQUEST),

    // === 기타 ===
    CSRF_TOKEN_MISSING("GATEWAY_018", "CSRF 토큰을 찾을 수 없습니다", HttpStatus.FORBIDDEN),
    AUDIENCE_INVALID("GATEWAY_019", "인증 토큰의 수신자가 유효하지 않습니다", HttpStatus.UNAUTHORIZED),
    ROLE_CLAIM_TYPE_ERROR("GATEWAY_020", "JWT 권한 클레임 형식이 올바르지 않습니다", HttpStatus.UNAUTHORIZED),
}
