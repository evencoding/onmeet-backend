package com.onmeet.email.service;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.EmailErrorCode;
import com.onmeet.email.config.GmailOAuth2Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailOAuth2TokenServiceTest {

    @Mock
    private GmailOAuth2Config config;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    private GmailOAuth2TokenService tokenService;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        tokenService = new GmailOAuth2TokenService(config, restTemplateBuilder);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity mockOkResponse(String accessToken) {
        return new ResponseEntity<>(Map.of("access_token", accessToken), HttpStatus.OK);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity mockOkResponseWithBody(Map<String, Object> body) {
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity mockErrorResponse(HttpStatus status) {
        return new ResponseEntity<>(null, status);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAccessToken_ValidCredentials_ReturnsToken() {
        // Given
        when(config.getClientId()).thenReturn("client-id");
        when(config.getClientSecret()).thenReturn("client-secret");
        when(config.getRefreshToken()).thenReturn("refresh-token");
        doReturn(mockOkResponse("ya29.new-access-token"))
                .when(restTemplate).postForEntity(anyString(), any(), any());

        // When
        String token = tokenService.getAccessToken();

        // Then
        assertEquals("ya29.new-access-token", token);
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), any());
    }

    @Test
    void getAccessToken_MissingClientId_ThrowsCredentialsMissing() {
        // Given: clientId is blank
        when(config.getClientId()).thenReturn("");

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.getAccessToken());
        assertEquals(EmailErrorCode.CREDENTIALS_MISSING, ex.getErrorCode());
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void getAccessToken_NullClientId_ThrowsCredentialsMissing() {
        // Given: clientId is null
        when(config.getClientId()).thenReturn(null);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.getAccessToken());
        assertEquals(EmailErrorCode.CREDENTIALS_MISSING, ex.getErrorCode());
    }

    @Test
    void getAccessToken_MissingClientSecret_ThrowsCredentialsMissing() {
        // Given
        when(config.getClientId()).thenReturn("valid-id");
        when(config.getClientSecret()).thenReturn("   "); // blank

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.getAccessToken());
        assertEquals(EmailErrorCode.CREDENTIALS_MISSING, ex.getErrorCode());
    }

    @Test
    void getAccessToken_MissingRefreshToken_ThrowsCredentialsMissing() {
        // Given
        when(config.getClientId()).thenReturn("valid-id");
        when(config.getClientSecret()).thenReturn("valid-secret");
        when(config.getRefreshToken()).thenReturn(null);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.getAccessToken());
        assertEquals(EmailErrorCode.CREDENTIALS_MISSING, ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAccessToken_HttpNonSuccessResponse_ThrowsTokenRefreshFailed() {
        // Given
        when(config.getClientId()).thenReturn("id");
        when(config.getClientSecret()).thenReturn("secret");
        when(config.getRefreshToken()).thenReturn("refresh");
        doReturn(mockErrorResponse(HttpStatus.BAD_REQUEST))
                .when(restTemplate).postForEntity(anyString(), any(), any());

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.getAccessToken());
        assertEquals(EmailErrorCode.TOKEN_REFRESH_FAILED, ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAccessToken_ResponseMissingAccessToken_ThrowsTokenResponseInvalid() {
        // Given: response is 200 but body has no "access_token" key
        when(config.getClientId()).thenReturn("id");
        when(config.getClientSecret()).thenReturn("secret");
        when(config.getRefreshToken()).thenReturn("refresh");

        Map<String, Object> responseBody = Map.of("token_type", "Bearer"); // no access_token
        doReturn(mockOkResponseWithBody(responseBody))
                .when(restTemplate).postForEntity(anyString(), any(), any());

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.getAccessToken());
        assertEquals(EmailErrorCode.TOKEN_RESPONSE_INVALID, ex.getErrorCode());
    }

    @Test
    void getAccessToken_NetworkError_ThrowsTokenNetworkError() {
        // Given: RestTemplate throws on network failure
        when(config.getClientId()).thenReturn("id");
        when(config.getClientSecret()).thenReturn("secret");
        when(config.getRefreshToken()).thenReturn("refresh");

        when(restTemplate.postForEntity(anyString(), any(), any()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.getAccessToken());
        assertEquals(EmailErrorCode.TOKEN_NETWORK_ERROR, ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAccessToken_CachedToken_DoesNotCallHttpAgain() {
        // Given: first call fetches a token
        when(config.getClientId()).thenReturn("id");
        when(config.getClientSecret()).thenReturn("secret");
        when(config.getRefreshToken()).thenReturn("refresh");
        doReturn(mockOkResponse("ya29.cached-token"))
                .when(restTemplate).postForEntity(anyString(), any(), any());

        // When: call getAccessToken twice
        String firstToken = tokenService.getAccessToken();
        String secondToken = tokenService.getAccessToken();

        // Then: HTTP call made only once; cached token returned on second call
        assertEquals("ya29.cached-token", firstToken);
        assertEquals("ya29.cached-token", secondToken);
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAccessToken_ExpiredCache_RefreshesToken() {
        // Given: after first fetch, simulate cache expiry and verify second HTTP call
        when(config.getClientId()).thenReturn("id");
        when(config.getClientSecret()).thenReturn("secret");
        when(config.getRefreshToken()).thenReturn("refresh");

        doReturn(mockOkResponse("ya29.first-token"))
                .doReturn(mockOkResponse("ya29.second-token"))
                .when(restTemplate).postForEntity(anyString(), any(), any());

        // First call
        String token1 = tokenService.getAccessToken();
        assertEquals("ya29.first-token", token1);

        // Force cache expiry by setting tokenExpiryMs to past timestamp
        ReflectionTestUtils.setField(tokenService, "tokenExpiryMs", System.currentTimeMillis() - 1000L);
        ReflectionTestUtils.setField(tokenService, "cachedToken", null);

        // Second call — should refresh
        String token2 = tokenService.getAccessToken();
        assertEquals("ya29.second-token", token2);

        verify(restTemplate, times(2)).postForEntity(anyString(), any(), any());
    }
}
