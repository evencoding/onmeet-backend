package com.onmeet.auth.controller;

import com.onmeet.auth.dto.AuthResponse;
import com.onmeet.auth.dto.CompanyLoginRequest;
import com.onmeet.auth.dto.CompanySignupRequest;
import com.onmeet.auth.dto.EmployeeInviteCreateRequest;
import com.onmeet.auth.dto.EmployeeInviteResponse;
import com.onmeet.auth.dto.EmployeeInviteSignupRequest;
import com.onmeet.auth.dto.EmployeeLoginRequest;
import com.onmeet.auth.dto.EmployeeSignupRequest;
import com.onmeet.auth.service.AuthService;
import com.onmeet.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/company-signup")
    public ApiResponse<AuthResponse> companySignup(@Valid @RequestBody CompanySignupRequest request) {
        return ApiResponse.ok(authService.companySignup(request));
    }

    @PostMapping("/employee-signup")
    public ApiResponse<AuthResponse> employeeSignup(@Valid @RequestBody EmployeeSignupRequest request) {
        return ApiResponse.ok(authService.employeeSignup(request));
    }

    @PostMapping("/company-login")
    public ApiResponse<AuthResponse> companyLogin(@Valid @RequestBody CompanyLoginRequest request) {
        return ApiResponse.ok(authService.companyLogin(request));
    }

    @PostMapping("/employee-login")
    public ApiResponse<AuthResponse> employeeLogin(@Valid @RequestBody EmployeeLoginRequest request) {
        return ApiResponse.ok(authService.employeeLogin(request));
    }

    @PostMapping("/employee-invites")
    public ApiResponse<EmployeeInviteResponse> createEmployeeInvite(@Valid @RequestBody EmployeeInviteCreateRequest request) {
        return ApiResponse.ok(authService.createEmployeeInvite(request));
    }

    @PostMapping("/employee-invite-signup")
    public ApiResponse<AuthResponse> employeeInviteSignup(@Valid @RequestBody EmployeeInviteSignupRequest request) {
        return ApiResponse.ok(authService.employeeInviteSignup(request));
    }
}
