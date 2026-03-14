package com.onmeet.common.client

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * BaseServiceClient 단위 테스트.
 * X-Gateway-Secret 헤더 주입, UserContext 헤더 포워딩, 에러 핸들링을 검증한다.
 */
class BaseServiceClientTest {

    // Concrete subclass exposing protected methods for testing
    private class TestServiceClient(restTemplate: RestTemplate, secret: String) : BaseServiceClient(restTemplate, secret) {
        fun callGetWithAuth(url: String, type: Class<String>) = getWithAuth(url, type)
        fun callPostWithAuth(url: String, body: Any, type: Class<String>) = postWithAuth(url, body, type)
        fun callPostWithAuthOrThrow(url: String, body: Any, type: Class<String>) = postWithAuthOrThrow(url, body, type)
        fun callDeleteWithAuth(url: String) = deleteWithAuth(url)
    }

    private lateinit var restTemplate: RestTemplate
    private lateinit var client: TestServiceClient
    private val sharedSecret = "test-gateway-secret"

    @BeforeEach
    fun setUp() {
        restTemplate = mock(RestTemplate::class.java)
        client = TestServiceClient(restTemplate, sharedSecret)
    }

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    private fun setRequestContextWithUserId(userId: Long) {
        val request = MockHttpServletRequest()
        request.addHeader("X-User-Id", userId.toString())
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    // ─── getWithAuth ─────────────────────────────────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `getWithAuth includes X-Gateway-Secret header`() {
        `when`(
            restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenReturn(ResponseEntity.ok("response"))

        client.callGetWithAuth("http://auth-service/api/users", String::class.java)

        val captor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<Unit>>
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), captor.capture(), eq(String::class.java))
        assertEquals(sharedSecret, captor.value.headers.getFirst("X-Gateway-Secret"))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `getWithAuth includes X-User-Id when UserContext has userId`() {
        setRequestContextWithUserId(42L)

        `when`(
            restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenReturn(ResponseEntity.ok("ok"))

        client.callGetWithAuth("http://auth-service/api/teams", String::class.java)

        val captor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<Unit>>
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), captor.capture(), eq(String::class.java))
        assertEquals("42", captor.value.headers.getFirst("X-User-Id"))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `getWithAuth omits X-User-Id when no request context`() {
        // No request context — non-request thread
        `when`(
            restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenReturn(ResponseEntity.ok("ok"))

        client.callGetWithAuth("http://auth-service/api/teams", String::class.java)

        val captor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<Unit>>
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), captor.capture(), eq(String::class.java))
        assertNull(captor.value.headers.getFirst("X-User-Id"))
        // Secret should still be there
        assertEquals(sharedSecret, captor.value.headers.getFirst("X-Gateway-Secret"))
    }

    @Test
    fun `getWithAuth returns response body on success`() {
        `when`(
            restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenReturn(ResponseEntity.ok("response body"))

        val result = client.callGetWithAuth("http://auth-service/api/users", String::class.java)

        assertEquals("response body", result)
    }

    @Test
    fun `getWithAuth returns null on HttpClientErrorException`() {
        `when`(
            restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND, "Not found"))

        val result = client.callGetWithAuth("http://auth-service/api/missing", String::class.java)

        assertNull(result)
    }

    @Test
    fun `getWithAuth returns null on HttpServerErrorException`() {
        `when`(
            restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error"))

        val result = client.callGetWithAuth("http://auth-service/api/crash", String::class.java)

        assertNull(result)
    }

    @Test
    fun `getWithAuth returns null on unexpected exception`() {
        `when`(
            restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenThrow(RuntimeException("connection refused"))

        val result = client.callGetWithAuth("http://down-service/api", String::class.java)

        assertNull(result)
    }

    // ─── postWithAuth ─────────────────────────────────────────────────────

    @Test
    fun `postWithAuth returns null on exception`() {
        `when`(
            restTemplate.postForObject(
                anyString(), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenThrow(RuntimeException("post failed"))

        val result = client.callPostWithAuth("http://service/api", "body", String::class.java)

        assertNull(result)
    }

    @Test
    fun `postWithAuth returns response body on success`() {
        `when`(
            restTemplate.postForObject(
                anyString(), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenReturn("created")

        val result = client.callPostWithAuth("http://service/api/resource", "request body", String::class.java)

        assertEquals("created", result)
    }

    // ─── postWithAuthOrThrow ──────────────────────────────────────────────

    @Test
    fun `postWithAuthOrThrow propagates exception`() {
        `when`(
            restTemplate.postForObject(
                anyString(), any(HttpEntity::class.java), eq(String::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST, "bad request"))

        assertThrows(HttpClientErrorException::class.java) {
            client.callPostWithAuthOrThrow("http://service/api", "data", String::class.java)
        }
    }

    // ─── deleteWithAuth ───────────────────────────────────────────────────

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `deleteWithAuth calls RestTemplate with DELETE method and gateway secret`() {
        `when`(
            restTemplate.exchange(
                anyString(), eq(HttpMethod.DELETE), any(HttpEntity::class.java), eq(Unit::class.java)
            )
        ).thenReturn(ResponseEntity.noContent().build())

        client.callDeleteWithAuth("http://service/api/resource/1")

        val captor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<Unit>>
        verify(restTemplate).exchange(
            anyString(), eq(HttpMethod.DELETE), captor.capture(), eq(Unit::class.java)
        )
        assertEquals(sharedSecret, captor.value.headers.getFirst("X-Gateway-Secret"))
    }
}
