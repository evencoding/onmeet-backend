package com.onmeet.email.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onmeet.common.dto.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1")
@Tag(name = "Email", description = "이메일 전송 관련 서비스 API")
public class EmailController {

    @Operation(
        summary = "이메일 서비스 헬스 체크",
        description = "이메일 서비스의 동작 상태를 확인합니다. 정상 동작 중이면 \"Email Service is running\" 문자열을 반환합니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "이메일 서비스 정상 동작 중"),
        @ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(value = "{\"code\":\"COMMON_INTERNAL_ERROR\",\"status\":500,\"message\":\"서버 내부 오류가 발생했습니다\",\"timestamp\":1710000000000}")
            )
        )
    })
    @GetMapping("/health")
    public String health() {
        return "Email Service is running";
    }
}
