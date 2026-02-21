package com.onmeet.notification.controller;

import com.onmeet.notification.dto.NotificationSettingDto;
import com.onmeet.notification.service.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notification/v1/settings")
@Tag(name = "Notification Settings", description = "알림 설정 API")
public class NotificationSettingController {

    private final NotificationSettingService settingService;

    @Operation(summary = "알림 설정 조회", description = "현재 사용자의 알림 설정을 조회합니다.")
    @GetMapping("/{userId}")
    public ResponseEntity<NotificationSettingDto> getSettings(@PathVariable Long userId) {
        return ResponseEntity.ok(settingService.getSettings(userId));
    }

    @Operation(summary = "알림 설정 업데이트", description = "사용자의 알림 설정을 업데이트합니다.")
    @PostMapping("/{userId}")
    public ResponseEntity<Void> updateSettings(@PathVariable Long userId, @RequestBody NotificationSettingDto dto) {
        settingService.updateSettings(userId, dto);
        return ResponseEntity.ok().build();
    }
}
