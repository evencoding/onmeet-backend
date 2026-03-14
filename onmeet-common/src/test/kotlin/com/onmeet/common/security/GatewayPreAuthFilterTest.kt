package com.onmeet.common.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

/**
 * GatewayPreAuthFilter 단위 테스트.
 * doFilter()를 통해 shouldNotFilter() 및 doFilterInternal() 동작을 검증한다.
 */
class GatewayPreAuthFilterTest {

    private val sharedSecret = "test-gateway-secret"
    private lateinit var filter: GatewayPreAuthFilter

    @BeforeEach
    fun setUp() {
        filter = GatewayPreAuthFilter(sharedSecret)
        SecurityContextHolder.clearContext()
    }

    // ─── shouldNotFilter 검증 ───────────────────────────────────────────

    @Test
    fun `actuator health path should bypass filter`() {
        val request = MockHttpServletRequest("GET", "/actuator/health")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request, "Chain should have been called for allowed actuator path")
        assertEquals(200, response.status)
    }

    @Test
    fun `actuator info path should bypass filter`() {
        val request = MockHttpServletRequest("GET", "/actuator/info")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request)
        assertEquals(200, response.status)
    }

    @Test
    fun `swagger-ui index path should bypass filter via AntPathMatcher`() {
        val request = MockHttpServletRequest("GET", "/swagger-ui/index.html")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request, "Chain should have been called for Swagger UI path")
        assertEquals(200, response.status)
    }

    @Test
    fun `swagger-ui root path should bypass filter`() {
        val request = MockHttpServletRequest("GET", "/swagger-ui/")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request)
    }

    @Test
    fun `v3 api-docs path should bypass filter via AntPathMatcher`() {
        val request = MockHttpServletRequest("GET", "/v3/api-docs/swagger-config")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request, "Chain should have been called for api-docs path")
        assertEquals(200, response.status)
    }

    @Test
    fun `api swagger-ui-exploit should NOT bypass filter`() {
        // /api/swagger-ui-exploit does NOT match /swagger-ui/** pattern
        val request = MockHttpServletRequest("GET", "/api/swagger-ui-exploit")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        // Filter runs, no valid secret → 403
        assertEquals(403, response.status)
        assertNull(chain.request, "Chain should NOT have been called for exploit path")
    }

    @Test
    fun `path with doc_json suffix should bypass filter`() {
        val request = MockHttpServletRequest("GET", "/file/doc.json")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request)
    }

    @Test
    fun `arbitrary actuator sub-path should bypass filter`() {
        val request = MockHttpServletRequest("GET", "/actuator/prometheus")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request)
    }

    // ─── doFilterInternal 검증 ──────────────────────────────────────────

    @Test
    fun `invalid gateway secret returns 403`() {
        val request = MockHttpServletRequest("GET", "/api/users")
        request.addHeader("X-Gateway-Secret", "wrong-secret")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(403, response.status)
        assertNull(chain.request, "Chain should not be called when secret is invalid")
    }

    @Test
    fun `missing gateway secret returns 403`() {
        val request = MockHttpServletRequest("GET", "/api/users")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(403, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `valid gateway secret with user headers sets authentication`() {
        val request = MockHttpServletRequest("GET", "/api/users")
        request.addHeader("X-Gateway-Secret", sharedSecret)
        request.addHeader("X-User-Id", "42")
        request.addHeader("X-User-Roles", "ROLE_USER,ROLE_ADMIN")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        assertNotNull(chain.request, "Chain should be called with valid secret")

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals("42", auth.principal)
        assertTrue(auth.authorities.any { it.authority == "ROLE_USER" })
        assertTrue(auth.authorities.any { it.authority == "ROLE_ADMIN" })
    }

    @Test
    fun `valid secret without user id does not set authentication`() {
        val request = MockHttpServletRequest("GET", "/api/public/ping")
        request.addHeader("X-Gateway-Secret", sharedSecret)
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        assertNotNull(chain.request)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `valid secret with blank user id does not set authentication`() {
        val request = MockHttpServletRequest("GET", "/api/public/ping")
        request.addHeader("X-Gateway-Secret", sharedSecret)
        request.addHeader("X-User-Id", "  ")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `valid secret with user id but no roles sets empty authorities`() {
        val request = MockHttpServletRequest("GET", "/api/teams")
        request.addHeader("X-Gateway-Secret", sharedSecret)
        request.addHeader("X-User-Id", "99")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals("99", auth.principal)
        assertTrue(auth.authorities.isEmpty())
    }
}
