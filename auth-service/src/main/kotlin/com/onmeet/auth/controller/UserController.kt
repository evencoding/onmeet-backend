package com.onmeet.auth.controller

import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.dto.JoinRequest
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
@Tag(name = "User (Internal)", description = "유저 정보 조회 등 내부용 API")
class UserController(
    private val userService: UserService
) {

    @Operation(summary = "유저 정보 조회", description = "User ID로 유저의 상세 정보를 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    ])
    @GetMapping("/{id}")
    fun getUserInfo(@PathVariable id: Long): ResponseEntity<UserResponseDto> {
        return ResponseEntity.ok(userService.getUserInfo(id))
    }
}
