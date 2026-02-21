package com.onmeet.notification.controller;

import com.onmeet.notification.dto.FcmTokenRequestDto;
import com.onmeet.notification.service.FcmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/v1/fcm")
@Tag(name = "FCM Token", description = "FCM 토큰 관리 API")
public class FcmTokenController {

    private final FcmService fcmService;

    @Operation(summary = "FCM 토큰 등록", description = "사용자의 디바이스 FCM 토큰을 등록합니다.")
    @PostMapping("/token/{userId}")
    public ResponseEntity<Void> registerToken(@PathVariable Long userId, @RequestBody FcmTokenRequestDto dto) {
        fcmService.registerToken(userId, dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "FCM 토큰 해제", description = "사용자의 FCM 토큰을 삭제합니다.")
    @DeleteMapping("/token/{userId}")
    public ResponseEntity<Void> unregisterToken(@PathVariable Long userId, @RequestParam String token) {
        fcmService.unregisterToken(userId, token);
        return ResponseEntity.ok().build();
    }
}
