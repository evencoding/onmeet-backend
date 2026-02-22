package com.onmeet.auth.controller

import com.onmeet.auth.dto.JobTitleRequest
import com.onmeet.auth.dto.JobTitleResponse
import com.onmeet.auth.dto.toResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.JobTitleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/job-titles")
@Tag(name = "Job Title Management", description = "직급 관리 API")
class JobTitleController(
    private val jobTitleService: JobTitleService
) {

    @Operation(summary = "직급 목록 조회", description = "현재 소속된 회사의 모든 직급 목록을 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    ])
    @GetMapping
    fun getJobTitles(@AuthenticationPrincipal user: User): ResponseEntity<List<JobTitleResponse>> =
        jobTitleService.getJobTitles(user.company)
            .map { it.toResponseDto() }
            .let { ResponseEntity.ok(it) }

    @Operation(summary = "직급 생성", description = "새로운 직급을 생성합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "생성 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음")
    ])
    @PostMapping
    fun createJobTitle(
        @AuthenticationPrincipal user: User,
        @RequestBody request: JobTitleRequest
    ): ResponseEntity<JobTitleResponse> =
        jobTitleService.createJobTitle(user, request)
            .let { ResponseEntity.ok(it.toResponseDto()) }

    @Operation(summary = "직급 수정", description = "기존 직급 정보를 수정합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "수정 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음"),
        ApiResponse(responseCode = "404", description = "직급을 찾을 수 없음")
    ])
    @PutMapping("/{id}")
    fun updateJobTitle(
        @AuthenticationPrincipal user: User,
        @PathVariable id: Long,
        @RequestBody request: JobTitleRequest
    ): ResponseEntity<JobTitleResponse> =
        jobTitleService.updateJobTitle(user, id, request)
            .let { ResponseEntity.ok(it.toResponseDto()) }

    @Operation(summary = "직급 삭제", description = "직급을 삭제합니다 (매니저 전용).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "삭제 성공"),
        ApiResponse(responseCode = "403", description = "권한 없음"),
        ApiResponse(responseCode = "404", description = "직급을 찾을 수 없음")
    ])
    @DeleteMapping("/{id}")
    fun deleteJobTitle(
        @AuthenticationPrincipal user: User,
        @PathVariable id: Long
    ): ResponseEntity<Void> =
        jobTitleService.deleteJobTitle(user, id)
            .let { ResponseEntity.ok().build() }
}
