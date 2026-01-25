package com.onmeet.user.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.user.dto.UserResponse;
import com.onmeet.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Users", description = "관리자 전용 사용자 조회 API")
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "사용자 단건 조회", description = "관리자 전용 사용자 조회 API")
    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> get(@PathVariable String userId) {
        return ApiResponse.ok(userService.get(userId));
    }

    @Operation(summary = "사용자 목록 조회", description = "관리자 전용 사용자 목록 API")
    @GetMapping
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.ok(userService.list());
    }
}
