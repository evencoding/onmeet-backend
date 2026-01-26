package com.onmeet.ai.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal Object principal) {
        String userId = principal != null ? principal.toString() : "Unknown";
        return "Hello from AI Service! User ID: " + userId;
    }
}
