package com.onmeet.auth.controller

import com.onmeet.auth.dto.*
import com.onmeet.auth.service.UserService
import com.onmeet.common.dto.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/users")
@Tag(name = "Internal User API", description = "내부 서비스 간 사용자 정보 조회 API (Gateway 인증 필요)")
class InternalUserController(
    private val userService: UserService
) {

    @Operation(
        summary = "사용자 정보 조회",
        description = "단일 사용자의 기본 정보를 조회합니다. (내부 서비스 전용)"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "사용자 정보 조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserInfoDto::class)
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [ExampleObject(
                    value = """{"status": 404, "message": "User not found: 123", "timestamp": 1234567890}"""
                )]
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class)
            )]
        )
    ])
    @GetMapping("/{userId}")
    fun getUserInfo(
        @PathVariable userId: Long
    ): ResponseEntity<UserInfoDto> =
        ResponseEntity.ok(userService.getUserInfoById(userId))

    @Operation(
        summary = "다중 사용자 정보 조회",
        description = "여러 사용자의 기본 정보를 일괄 조회합니다. (내부 서비스 전용)"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "다중 사용자 정보 조회 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = BatchUserInfoResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 필수 필드 누락",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class)
            )]
        )
    ])
    @PostMapping("/batch")
    fun getBatchUserInfo(
        @RequestBody request: BatchUserInfoRequest
    ): ResponseEntity<BatchUserInfoResponse> {
        val users = userService.getBatchUserInfo(request.userIds)
        return ResponseEntity.ok(BatchUserInfoResponse(users))
    }

    @Operation(
        summary = "사용자 존재 여부 확인",
        description = "단일 사용자의 존재 여부를 확인합니다. (내부 서비스 전용)"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "사용자 존재 여부 확인 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserExistsResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class)
            )]
        )
    ])
    @GetMapping("/{userId}/exists")
    fun checkUserExists(
        @PathVariable userId: Long
    ): ResponseEntity<UserExistsResponse> {
        val exists = userService.existsById(userId)
        return ResponseEntity.ok(UserExistsResponse(userId, exists))
    }

    @Operation(
        summary = "다중 사용자 존재 여부 확인",
        description = "여러 사용자의 존재 여부를 일괄 확인합니다. (내부 서비스 전용)"
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "다중 사용자 존재 여부 확인 성공",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = BatchUserExistsResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 - 필수 필드 누락",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "500",
            description = "서버 내부 오류",
            content = [Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class)
            )]
        )
    ])
    @PostMapping("/exists/batch")
    fun checkBatchUserExists(
        @RequestBody request: BatchUserExistsRequest
    ): ResponseEntity<BatchUserExistsResponse> {
        val existsMap = userService.existsByIds(request.userIds)
        val users = existsMap.map { (userId, exists) -> UserExistsResponse(userId, exists) }
        return ResponseEntity.ok(BatchUserExistsResponse(users))
    }
}
