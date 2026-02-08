package com.onmeet.common.client

import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 마이크로서비스 간 통신을 위한 기반 클라이언트 클래스.
 * 쿠키 포워딩 및 공통 REST 요청 로직을 처리합니다.
 */
abstract class BaseServiceClient(
    protected val restTemplate: RestTemplate
) {
    protected val log = LoggerFactory.getLogger(this::class.java)

    /**
     * 현재 요청의 쿠키를 포함하여 GET 요청을 보냅니다.
     * 
     * @param url 요청을 보낼 대상 URL
     * @param responseType 응답 데이터 클래스 타입
     * @return 통신 결과 body (실패 시 null)
     */
    protected fun <T> getWithAuth(url: String, responseType: Class<T>): T? {
        val headers = HttpHeaders().apply {
            // 현재 쓰레드의 Request에서 쿠키를 추출하여 전달 (서비스 간 인증 전파)
            (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.getHeader(HttpHeaders.COOKIE)?.let {
                add(HttpHeaders.COOKIE, it)
            }
        }
        
        val entity = HttpEntity<Unit>(headers)

        return try {
            restTemplate.exchange(url, HttpMethod.GET, entity, responseType).body
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
}
