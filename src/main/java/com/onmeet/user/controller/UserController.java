package com.onmeet.user.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.user.dto.UserCreateRequest;
import com.onmeet.user.dto.UserResponse;
import com.onmeet.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> get(@PathVariable String userId) {
        return ApiResponse.ok(userService.get(userId));
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.ok(userService.list());
    }
}
