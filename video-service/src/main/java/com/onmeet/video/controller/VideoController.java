package com.onmeet.video.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1")
@Tag(name = "Video", description = "화상 통화/회의 관련 서비스 API")
public class VideoController {

    @Operation(summary = "비디오 서비스 상태/정보 조회", description = "비디오 서비스의 상태 및 내 정보를 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/me")
    public String me(@AuthenticationPrincipal String userId) {
        return "Hello from Video Service! User ID: " + (userId != null ? userId : "Unknown");
    }

    @Operation(summary = "회의실 입장 (권한 체크 예시)", description = "팀 멤버만 회의실에 입장할 수 있습니다.")
    @GetMapping("/teams/{teamId}/meetings")
    @PreAuthorize("@teamSecurity.isMemberOf(#teamId, principal)")
    public String getMeetings(@PathVariable Long teamId, @AuthenticationPrincipal String userId) {
        return "Meeting list for team " + teamId + " for user " + userId;
    }
}
