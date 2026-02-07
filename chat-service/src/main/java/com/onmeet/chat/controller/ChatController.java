package com.onmeet.chat.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal String userId) {
        return "Hello from Chat Service! User ID: " + (userId != null ? userId : "Unknown");
    }
}
