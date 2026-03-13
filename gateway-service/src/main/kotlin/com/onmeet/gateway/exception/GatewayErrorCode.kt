package com.onmeet.gateway.exception

import org.springframework.http.HttpStatus

enum class GatewayErrorCode(
    val code: String,
    val status: Int,
    val message: String
) {
    // JWT / 인증 관련 (001~005)
    MISSING_TOKEN("GATEWAY_001", HttpStatus.UNAUTHORIZED.value(), "인증 토큰이 없습니다."),
    EXPIRED_TOKEN("GATEWAY_002", HttpStatus.UNAUTHORIZED.value(), "토큰이 만료되었습니다."),
    INVALID_TOKEN("GATEWAY_003", HttpStatus.UNAUTHORIZED.value(), "유효하지 않은 토큰입니다."),
    UNSUPPORTED_JWT_ALGORITHM("GATEWAY_004", HttpStatus.UNAUTHORIZED.value(), "지원하지 않는 JWT 알고리즘입니다."),
    MALFORMED_JWT("GATEWAY_005", HttpStatus.UNAUTHORIZED.value(), "잘못된 JWT 형식입니다."),

    // 인가 관련 (006~007)
    AUTHENTICATION_FAILED("GATEWAY_006", HttpStatus.UNAUTHORIZED.value(), "인증에 실패하였습니다."),
    ACCESS_DENIED("GATEWAY_007", HttpStatus.FORBIDDEN.value(), "접근이 거부되었습니다."),

    // 요청 관련 (008~013)
    NOT_FOUND("GATEWAY_008", HttpStatus.NOT_FOUND.value(), "요청한 리소스를 찾을 수 없습니다."),
    BAD_REQUEST("GATEWAY_009", HttpStatus.BAD_REQUEST.value(), "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED("GATEWAY_010", HttpStatus.METHOD_NOT_ALLOWED.value(), "허용되지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE("GATEWAY_011", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "지원하지 않는 미디어 타입입니다."),
    TOO_MANY_REQUESTS("GATEWAY_012", HttpStatus.TOO_MANY_REQUESTS.value(), "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    PAYLOAD_TOO_LARGE("GATEWAY_013", HttpStatus.PAYLOAD_TOO_LARGE.value(), "요청 본문이 너무 큽니다."),

    // 업스트림 서비스 관련 (014~016)
    SERVICE_UNAVAILABLE("GATEWAY_014", HttpStatus.SERVICE_UNAVAILABLE.value(), "서비스에 연결할 수 없습니다."),
    GATEWAY_TIMEOUT("GATEWAY_015", HttpStatus.GATEWAY_TIMEOUT.value(), "요청 처리 시간이 초과되었습니다."),
    BAD_GATEWAY("GATEWAY_016", HttpStatus.BAD_GATEWAY.value(), "업스트림 서비스 오류가 발생했습니다."),

    // 서버 관련 (017~020)
    RESPONSE_TOO_LARGE("GATEWAY_017", HttpStatus.INTERNAL_SERVER_ERROR.value(), "응답 크기가 너무 큽니다."),
    INTERNAL_SERVER_ERROR("GATEWAY_018", HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버 내부 오류가 발생했습니다."),
    UNKNOWN_ERROR("GATEWAY_019", HttpStatus.INTERNAL_SERVER_ERROR.value(), "알 수 없는 오류가 발생했습니다."),
    CIRCUIT_BREAKER_OPEN("GATEWAY_020", HttpStatus.SERVICE_UNAVAILABLE.value(), "서비스가 일시적으로 중단되었습니다.")
}
