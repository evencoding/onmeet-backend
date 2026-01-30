package com.onmeet.notification.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal String userId) {
        return "Hello from Notification Service! User ID: " + (userId != null ? userId : "Unknown");
    }
}
