package com.onmeet.video.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/videos")
public class VideoController {

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal String userId) {
        return "Hello from Video Service! User ID: " + (userId != null ? userId : "Unknown");
    }
}
