package com.onmeet.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onmeet.notification.dto.FcmTokenRequestDto;
import com.onmeet.notification.entity.FcmToken;
import com.onmeet.notification.repository.FcmTokenRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

    @Mock
    FcmTokenRepository fcmTokenRepository;

    @Mock
    FcmPushRetryService fcmPushRetryService;

    @InjectMocks
    FcmService fcmService;

    private static final Long USER_ID = 1L;

    @Nested
    @DisplayName("registerToken")
    class RegisterToken {

        @Test
        @DisplayName("새 기기 등록 - 토큰 저장")
        void registerToken_newDevice() {
            FcmTokenRequestDto dto = new FcmTokenRequestDto();
            dto.setToken("new-token");
            dto.setDeviceId("device-1");
            dto.setDeviceType("WEB");

            when(fcmTokenRepository.findByUserIdAndDeviceId(USER_ID, "device-1"))
                    .thenReturn(Optional.empty());

            fcmService.registerToken(USER_ID, dto);

            verify(fcmTokenRepository).save(any(FcmToken.class));
        }

        @Test
        @DisplayName("기존 기기 - 토큰이 다르면 업데이트")
        void registerToken_existingDevice_differentToken() {
            FcmToken existing = FcmToken.builder()
                    .userId(USER_ID)
                    .token("old-token")
                    .deviceId("device-1")
                    .deviceType("WEB")
                    .build();

            FcmTokenRequestDto dto = new FcmTokenRequestDto();
            dto.setToken("new-token");
            dto.setDeviceId("device-1");
            dto.setDeviceType("WEB");

            when(fcmTokenRepository.findByUserIdAndDeviceId(USER_ID, "device-1"))
                    .thenReturn(Optional.of(existing));

            fcmService.registerToken(USER_ID, dto);

            assertThat(existing.getToken()).isEqualTo("new-token");
            verify(fcmTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("기존 기기 - 토큰이 같으면 변경 없음")
        void registerToken_existingDevice_sameToken() {
            FcmToken existing = FcmToken.builder()
                    .userId(USER_ID)
                    .token("same-token")
                    .deviceId("device-1")
                    .deviceType("WEB")
                    .build();

            FcmTokenRequestDto dto = new FcmTokenRequestDto();
            dto.setToken("same-token");
            dto.setDeviceId("device-1");
            dto.setDeviceType("WEB");

            when(fcmTokenRepository.findByUserIdAndDeviceId(USER_ID, "device-1"))
                    .thenReturn(Optional.of(existing));

            fcmService.registerToken(USER_ID, dto);

            assertThat(existing.getToken()).isEqualTo("same-token");
            verify(fcmTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("unregisterToken")
    class UnregisterToken {

        @Test
        @DisplayName("토큰 해제")
        void unregisterToken_success() {
            fcmService.unregisterToken(USER_ID, "some-token");

            verify(fcmTokenRepository).deleteByUserIdAndToken(USER_ID, "some-token");
        }
    }

    @Nested
    @DisplayName("getTokensByUserId")
    class GetTokens {

        @Test
        @DisplayName("사용자의 모든 토큰 반환")
        void getTokensByUserId_success() {
            FcmToken t1 = FcmToken.builder().userId(USER_ID).token("token-1").deviceId("d1").build();
            FcmToken t2 = FcmToken.builder().userId(USER_ID).token("token-2").deviceId("d2").build();

            when(fcmTokenRepository.findByUserId(USER_ID)).thenReturn(List.of(t1, t2));

            List<String> tokens = fcmService.getTokensByUserId(USER_ID);

            assertThat(tokens).containsExactly("token-1", "token-2");
        }

        @Test
        @DisplayName("토큰 없으면 빈 리스트 반환")
        void getTokensByUserId_empty() {
            when(fcmTokenRepository.findByUserId(USER_ID)).thenReturn(List.of());

            List<String> tokens = fcmService.getTokensByUserId(USER_ID);

            assertThat(tokens).isEmpty();
        }
    }
}
