package com.onmeet.common.exception.errorcode

import com.onmeet.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/**
 * email-service 에러 코드 정의.
 */
enum class EmailErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {

    // === 템플릿 ===
    INVALID_TEMPLATE("EMAIL_001", "허용되지 않은 이메일 템플릿입니다", HttpStatus.BAD_REQUEST),

    // === OAuth2 ===
    OAUTH2_TOKEN_CONFIG_FAILED("EMAIL_002", "Gmail OAuth2 토큰 설정에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === 이메일 전송 ===
    TEMPLATE_RENDER_FAILED("EMAIL_003", "이메일 템플릿 처리 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    MESSAGE_BUILD_FAILED("EMAIL_004", "이메일 메시지 구성 중 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    SEND_FAILED("EMAIL_005", "이메일 전송에 실패했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    GENERAL_ERROR("EMAIL_006", "이메일 처리 중 알 수 없는 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // === Gmail 인증 ===
    CREDENTIALS_MISSING("EMAIL_007", "Gmail OAuth2 인증 정보가 설정되지 않았습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    TOKEN_RESPONSE_INVALID("EMAIL_008", "Gmail 토큰 응답에서 액세스 토큰을 찾을 수 없습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    TOKEN_REFRESH_FAILED("EMAIL_009", "Gmail 액세스 토큰 갱신에 실패했습니다", HttpStatus.BAD_GATEWAY),
    TOKEN_NETWORK_ERROR("EMAIL_010", "Gmail 토큰 서버와 통신 중 오류가 발생했습니다", HttpStatus.SERVICE_UNAVAILABLE),

    // === Kafka ===
    KAFKA_PARSE_FAILED("EMAIL_011", "Kafka 메시지의 JSON 형식이 올바르지 않습니다", HttpStatus.INTERNAL_SERVER_ERROR),
}
