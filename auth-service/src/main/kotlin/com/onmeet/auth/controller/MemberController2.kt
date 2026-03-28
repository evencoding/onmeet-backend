package com.onmeet.auth.controller

import com.onmeet.auth.dto.PageResponse
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/members")
@Tag(name = "Member", description = "멤버 조회 API (인증된 사용자 모두 접근 가능)")
class MemberController2(
    private val userService: UserService
) {

    @Operation(
        summary = "초대 가능한 멤버 목록",
        description = "MANAGER/ADMIN: 전체 사원 목록, USER: 자신이 속한 팀의 팀원 목록을 반환합니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/invitable")
    fun getInvitableMembers(
        @AuthenticationPrincipal user: User,
        @PageableDefault(size = 100) pageable: Pageable
    ): ResponseEntity<PageResponse<UserResponseDto>> =
        ResponseEntity.ok(userService.getInvitableMembers(user, pageable))
}
