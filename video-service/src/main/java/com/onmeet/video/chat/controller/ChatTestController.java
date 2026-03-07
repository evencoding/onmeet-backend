package com.onmeet.video.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatTestController {

    @GetMapping("/chat-test")
    public String chatTestPage() {
        return "chat";
    }
}
