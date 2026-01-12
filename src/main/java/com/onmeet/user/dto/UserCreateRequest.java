package com.onmeet.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
    @Email @NotBlank String email,
    @NotBlank String name
) {
}
