package com.onmeet.auth.dto;

import com.onmeet.user.dto.UserResponse;

public record AuthResponse(
    String accessToken,
    String tokenType,
    UserResponse user,
    CompanyResponse company,
    EmployeeResponse employee
) {
}
