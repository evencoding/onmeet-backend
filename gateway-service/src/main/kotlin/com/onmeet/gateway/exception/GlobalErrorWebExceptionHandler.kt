package com.onmeet.gateway.exception

import com.fasterxml.jackson.databind.ObjectMapper
import com.onmeet.gateway.dto.ErrorDetail
import com.onmeet.gateway.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.ConnectException
import java.util.concurrent.TimeoutException

@Component
@Order(-2)
class GlobalErrorWebExceptionHandler(
    private val objectMapper: ObjectMapper
) : ErrorWebExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler::class.java)

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val errorCode = resolveErrorCode(ex)
        val logLevel = if (errorCode.status >= 500) "ERROR" else "WARN"

        if (logLevel == "ERROR") {
            logger.error("[{}] {} - {}", errorCode.code, ex.javaClass.simpleName, ex.message, ex)
        } else {
            logger.warn("[{}] {} - {}", errorCode.code, ex.javaClass.simpleName, ex.message)
        }

        val response = exchange.response
        response.statusCode = HttpStatus.valueOf(errorCode.status)
        response.headers.contentType = MediaType.APPLICATION_JSON

        val errorResponse = ErrorResponse(
            error = ErrorDetail(
                code = errorCode.code,
                status = errorCode.status,
                message = errorCode.message
            )
        )

        val bytes = objectMapper.writeValueAsBytes(errorResponse)
        val buffer = response.bufferFactory().wrap(bytes)

        return response.writeWith(Mono.just(buffer))
    }

    private fun resolveErrorCode(ex: Throwable): GatewayErrorCode {
        return when {
            // JWT 만료 (JwtValidationException은 BadJwtException의 상위가 아니므로 먼저 확인)
            ex is JwtValidationException -> GatewayErrorCode.EXPIRED_TOKEN

            // JWT 형식 불량
            ex is BadJwtException -> GatewayErrorCode.MALFORMED_JWT

            // OAuth2 인증 예외 (InvalidBearerTokenException 포함) - cause 기반 세분화
            ex is OAuth2AuthenticationException -> resolveOAuth2Error(ex)

            // Spring Security 인증 실패 (토큰 없음 등)
            ex is AuthenticationException -> GatewayErrorCode.MISSING_TOKEN

            // 접근 거부
            ex is AccessDeniedException -> GatewayErrorCode.ACCESS_DENIED

            // 업스트림 연결 실패
            ex is ConnectException || ex.cause is ConnectException -> GatewayErrorCode.SERVICE_UNAVAILABLE

            // 타임아웃
            ex is TimeoutException || ex.cause is TimeoutException -> GatewayErrorCode.GATEWAY_TIMEOUT

            // ResponseStatusException (Spring WebFlux 4xx/5xx)
            ex is ResponseStatusException -> resolveResponseStatusException(ex)

            // 그 외
            else -> GatewayErrorCode.UNKNOWN_ERROR
        }
    }

    private fun resolveOAuth2Error(ex: OAuth2AuthenticationException): GatewayErrorCode {
        val cause = ex.cause
        return when {
            cause is JwtValidationException -> GatewayErrorCode.EXPIRED_TOKEN
            cause is BadJwtException -> GatewayErrorCode.MALFORMED_JWT
            ex.error.description?.contains("expired", ignoreCase = true) == true -> GatewayErrorCode.EXPIRED_TOKEN
            ex.error.description?.contains("algorithm", ignoreCase = true) == true -> GatewayErrorCode.UNSUPPORTED_JWT_ALGORITHM
            ex.error.description?.contains("malformed", ignoreCase = true) == true -> GatewayErrorCode.MALFORMED_JWT
            else -> GatewayErrorCode.INVALID_TOKEN
        }
    }

    private fun resolveResponseStatusException(ex: ResponseStatusException): GatewayErrorCode {
        return when (ex.statusCode) {
            HttpStatus.NOT_FOUND -> GatewayErrorCode.NOT_FOUND
            HttpStatus.BAD_REQUEST -> GatewayErrorCode.BAD_REQUEST
            HttpStatus.METHOD_NOT_ALLOWED -> GatewayErrorCode.METHOD_NOT_ALLOWED
            HttpStatus.UNSUPPORTED_MEDIA_TYPE -> GatewayErrorCode.UNSUPPORTED_MEDIA_TYPE
            HttpStatus.TOO_MANY_REQUESTS -> GatewayErrorCode.TOO_MANY_REQUESTS
            HttpStatus.PAYLOAD_TOO_LARGE -> GatewayErrorCode.PAYLOAD_TOO_LARGE
            HttpStatus.SERVICE_UNAVAILABLE -> GatewayErrorCode.SERVICE_UNAVAILABLE
            HttpStatus.GATEWAY_TIMEOUT -> GatewayErrorCode.GATEWAY_TIMEOUT
            HttpStatus.BAD_GATEWAY -> GatewayErrorCode.BAD_GATEWAY
            else -> GatewayErrorCode.INTERNAL_SERVER_ERROR
        }
    }
}
