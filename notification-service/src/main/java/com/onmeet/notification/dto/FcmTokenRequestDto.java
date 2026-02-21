package com.onmeet.notification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FcmTokenRequestDto {
    private String token;
    private String deviceId; // 기기 고유 식별자 (UUID 등)
    private String deviceType; // "WEB", "ANDROID", "IOS"
}
