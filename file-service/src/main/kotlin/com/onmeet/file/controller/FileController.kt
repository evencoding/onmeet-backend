package com.onmeet.file.controller

import com.onmeet.common.dto.ErrorResponse
import com.onmeet.common.exception.InsufficientPermissionException
import com.onmeet.common.security.UserContext
import com.onmeet.file.dto.FileResponseDto
import com.onmeet.file.entity.FileMetadata
import com.onmeet.file.entity.toResponseDto
import com.onmeet.file.service.FileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Tag(name = "File API", description = "파일 업로드, 조회 및 삭제를 위한 API")
@RestController
@RequestMapping("/api/v1/files")
class FileController(
    private val fileService: FileService
) {

    @Operation(
        summary = "동기 파일 업로드",
        description = "여러 파일을 동기적으로 업로드하고 상세 정보를 즉시 반환합니다.",
        responses = [
            ApiResponse(responseCode = "200", description = "업로드 성공", content = [Content(schema = Schema(implementation = FileResponseDto::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "500", description = "S3 업로드 또는 서버 내부 오류", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
        ]
    )
    @PostMapping("/upload")
    fun uploadFiles(
        @Parameter(description = "업로드할 파일 리스트", required = true)
        @RequestParam("files") files: List<MultipartFile>,
        
        @Parameter(description = "파일 카테고리 (예: profile, audio, chat)", required = true)
        @RequestParam("category") category: String,
        
        @Parameter(description = "소유자 유형 (USER, TEAM, COMPANY, SYSTEM)", required = false)
        @RequestParam("ownerType", required = false) ownerType: String?,
        
        @Parameter(description = "소유자 ID (지정하지 않으면 현재 uploaderId 사용)", required = false)
        @RequestParam("ownerId", required = false) ownerId: String?
    ): ResponseEntity<List<FileResponseDto>> =
        UserContext.getUserId().orElse(null).let { uploaderId ->
            fileService.uploadFiles(files, category, uploaderId, ownerType, ownerId)
                .map { it.toResponseDto() }
                .let { ResponseEntity.ok(it) }
        }

    @Operation(
        summary = "비동기 파일 업로드",
        description = "파일 업로드를 비동기로 시작하고 즉시 접수 응답을 보냅니다. 결과는 Kafka 콜백을 통해 전달됩니다.",
        responses = [
            ApiResponse(responseCode = "202", description = "업로드 접수됨"),
            ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "500", description = "서버 내부 오류", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
        ]
    )
    @PostMapping("/upload-async")
    fun uploadFilesAsync(
        @Parameter(description = "업로드할 파일 리스트", required = true)
        @RequestParam("files") files: List<MultipartFile>,
        
        @Parameter(description = "파일 카테고리", required = true)
        @RequestParam("category") category: String,
        
        @Parameter(description = "소유자 유형", required = false)
        @RequestParam("ownerType", required = false) ownerType: String?,
        
        @Parameter(description = "소유자 ID", required = false)
        @RequestParam("ownerId", required = false) ownerId: String?,
        
        @Parameter(description = "결과를 수신할 Kafka 토픽", required = false)
        @RequestParam("callbackTopic", required = false) callbackTopic: String?,
        
        @Parameter(description = "요청 추적 목적의 ID", required = false)
        @RequestParam("correlationId", required = false) correlationId: String?
    ): ResponseEntity<Map<String, Any>> =
        UserContext.getUserId().orElse(null).let { uploaderId ->
            files.map { file ->
                Triple(file.bytes, file.originalFilename ?: "unknown", file.contentType ?: "application/octet-stream")
            }.let { fileDataList ->
                fileService.uploadFilesAsync(fileDataList, category, uploaderId, ownerType, ownerId, callbackTopic, correlationId)
            }
            ResponseEntity.accepted().body(mapOf(
                "message" to "Batch file upload started asynchronously. Processing results will be sent to Kafka.",
                "fileCount" to files.size,
                "callbackTopic" to (callbackTopic ?: "file-upload-events")
            ))
        }

    @Operation(
        summary = "파일 정보 조회", 
        description = "파일 ID를 기반으로 상세 정보를 조회합니다.",
        responses = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
        ]
    )
    @GetMapping("/{fileId}")
    fun getFileInfo(
        @Parameter(description = "파일 고유 ID", required = true)
        @PathVariable fileId: Long
    ): ResponseEntity<FileResponseDto> =
        fileService.getFileMetadata(fileId).toResponseDto().let { ResponseEntity.ok(it) }

    @Operation(
        summary = "파일 삭제", 
        description = "파일 ID를 기반으로 S3 및 DB에서 파일을 삭제합니다.",
        responses = [
            ApiResponse(responseCode = "204", description = "삭제 성공"),
            ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "403", description = "삭제 권한 없음", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
            ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorResponse::class))])
        ]
    )
    @DeleteMapping("/{fileId}")
    fun deleteFile(
        @Parameter(description = "파일 고유 ID", required = true)
        @PathVariable fileId: Long
    ): ResponseEntity<Void> =
        UserContext.getRequiredUserId().let { requesterId ->
            fileService.deleteFile(fileId, requesterId)
            ResponseEntity.noContent().build()
        }
}
