package com.onmeet.notification.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.notification.dto.NotificationCreateRequest;
import com.onmeet.notification.dto.NotificationResponse;
import com.onmeet.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ApiResponse<NotificationResponse> create(@Valid @RequestBody NotificationCreateRequest request) {
        return ApiResponse.ok(notificationService.create(request));
    }
}
