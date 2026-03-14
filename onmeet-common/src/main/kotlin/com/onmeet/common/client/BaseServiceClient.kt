package com.onmeet.common.client

import com.onmeet.common.security.UserContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate

/**
 * 마이크로서비스 간 통신을 위한 기반 클라이언트 클래스.
 * X-Gateway-Secret 헤더를 통한 서비스 간 인증 및 공통 REST 요청 로직을 처리합니다.
 */
abstract class BaseServiceClient(
    protected val restTemplate: RestTemplate,
    private val gatewaySecret: String
) {
    protected val log = LoggerFactory.getLogger(this::class.java)

    private fun buildAuthHeaders(): HttpHeaders = HttpHeaders().apply {
        set("X-Gateway-Secret", gatewaySecret)
        UserContext.getUserId().ifPresent { set(UserContext.USER_ID_HEADER, it.toString()) }
    }

    protected fun <T> getWithAuth(url: String, responseType: Class<T>): T? {
        return try {
            restTemplate.exchange(url, HttpMethod.GET, HttpEntity<Unit>(buildAuthHeaders()), responseType).body
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            log.warn("Client error during API request. URL: {}, Status: {}, Message: {}", url, e.statusCode, e.message)
            null
        } catch (e: org.springframework.web.client.HttpServerErrorException) {
            log.error("Server error during API request. URL: {}, Status: {}, Message: {}", url, e.statusCode, e.message)
            null
        } catch (e: Exception) {
            log.error("Unexpected error during API request. URL: {}, Message: {}", url, e.message, e)
            null
        }
    }

    protected fun <T, R> postWithAuth(url: String, requestBody: R, responseType: Class<T>): T? {
        return try {
            postWithAuthOrThrow(url, requestBody, responseType)
        } catch (e: Exception) {
            log.error("Error during POST API request. URL: {}, Message: {}", url, e.message)
            null
        }
    }

    protected fun <T, R> postWithAuthOrThrow(url: String, requestBody: R, responseType: Class<T>): T? {
        return restTemplate.postForObject(url, HttpEntity(requestBody, buildAuthHeaders()), responseType)
    }

    protected fun <T> postMultipartWithAuth(url: String, body: org.springframework.util.MultiValueMap<String, Any>, responseType: Class<T>): T? {
        return try {
            postMultipartWithAuthOrThrow(url, body, responseType)
        } catch (e: Exception) {
            log.error("Error during Multipart POST API request. URL: {}, Message: {}", url, e.message)
            null
        }
    }

    protected fun <T> postMultipartWithAuthOrThrow(url: String, body: org.springframework.util.MultiValueMap<String, Any>, responseType: Class<T>): T? {
        val headers = buildAuthHeaders().apply {
            contentType = org.springframework.http.MediaType.MULTIPART_FORM_DATA
        }
        return restTemplate.postForObject(url, HttpEntity(body, headers), responseType)
    }

    protected fun deleteWithAuth(url: String) {
        restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity<Unit>(buildAuthHeaders()), Unit::class.java)
    }
}
