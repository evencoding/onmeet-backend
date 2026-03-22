package com.onmeet.auth.client

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.ResourceAccessException
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any as anyArg
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq

import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [FileClient::class])
@ImportAutoConfiguration(CircuitBreakerAutoConfiguration::class, AopAutoConfiguration::class)
@TestPropertySource(properties = ["onmeet.file.internal-url=http://localhost:8080"])
class FileClientTest {

    @Autowired
    private lateinit var fileClient: FileClient

    @MockBean
    private lateinit var restTemplate: RestTemplate
    
    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Test
    fun `generateDefaultProfileImage should return null when service is down`() {
        // Given
        `when`(restTemplate.postForObject(anyString(), anyArg(), eq(FileMetadataResponse::class.java)))
            .thenThrow(ResourceAccessException("Service down"))
            
        // When
        val result = fileClient.generateDefaultProfileImage("test-user", "1")
        
        // Then
        assertNull(result, "Fallback should return null on failure")
    }
}
