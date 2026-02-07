package com.onmeet.auth.controller

import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.dto.JoinRequest
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class UserController(
    private val userService: UserService
) {

    @GetMapping("/users/{id}")
    fun getUserInfo(@PathVariable id: Long): ResponseEntity<UserResponseDto> {
        return ResponseEntity.ok(userService.getUserInfo(id))
    }
}
