package com.onmeet.image.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/image")
public class ImageController {

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal String userId) {
        return "Hello from Image Service! User ID: " + (userId != null ? userId : "Unknown");
    }
}
