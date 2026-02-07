package com.onmeet.email.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping
@Tag(name = "Email", description = "이메일 전송 관련 서비스 API")
public class EmailController {

    @Operation(summary = "이메일 서비스 헬스 체크", description = "이메일 서비스의 동작 상태를 확인합니다.")
    @GetMapping("/health")
    public String health() {
        return "Email Service is running";
    }
}
