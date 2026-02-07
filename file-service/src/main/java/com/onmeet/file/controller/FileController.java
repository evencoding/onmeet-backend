package com.onmeet.file.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "File", description = "파일 업로드/다운로드 관련 서비스 API")
public class FileController {

    @Operation(summary = "파일 서비스 헬스 체크", description = "파일 서비스의 동작 상태를 확인합니다.")
    @GetMapping("/")
    public String healthCheck() {
        return "File Service is operational";
    }
}
