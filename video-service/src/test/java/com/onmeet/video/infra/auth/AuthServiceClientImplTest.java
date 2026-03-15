package com.onmeet.video.infra.auth;

import com.onmeet.video.config.AuthServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [ITEM-7] AuthServiceClient 실패 시 에러 전파 전략 검증.
 * 더미 데이터 반환 대신 예외를 던지는지 확인한다.
 */
class AuthServiceClientImplTest {

    private RestTemplate restTemplate;
    private AuthServiceProperties authServiceProperties;
    private AuthServiceClientImpl authServiceClient;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        authServiceProperties = mock(AuthServiceProperties.class);
        when(authServiceProperties.getInternalUrl()).thenReturn("http://auth-service:8081");
        authServiceClient = new AuthServiceClientImpl(restTemplate, authServiceProperties, "test-secret");
    }

    @Test
    void getUserInfo_whenAuthServiceFails_shouldThrowException() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(AuthServiceClient.UserInfo.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        // [ITEM-7]: "Unknown User" 더미 반환 대신 예외가 전파되어야 한다
        assertThatThrownBy(() -> authServiceClient.getUserInfo(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("auth-service");
    }

    @Test
    void getBatchUserInfo_whenAuthServiceFails_shouldThrowException() {
        when(restTemplate.exchange(anyString(), any(), any(), eq(AuthServiceClient.BatchUserInfoResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> authServiceClient.getBatchUserInfo(java.util.List.of(1L, 2L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("auth-service");
    }
}
