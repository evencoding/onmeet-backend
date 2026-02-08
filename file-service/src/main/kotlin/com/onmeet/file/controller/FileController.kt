package com.onmeet.file.controller

import com.onmeet.common.security.UserContext
import com.onmeet.file.dto.FileResponseDto
import com.onmeet.file.entity.FileMetadata
import com.onmeet.file.service.FileService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/files")
class FileController(
    private val fileService: FileService
) {

    @PostMapping("/upload")
    fun uploadFile(
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<FileResponseDto> {
        val uploaderId = UserContext.getRequiredUserId()
        val metadata = fileService.uploadFile(file, uploaderId)
        return ResponseEntity.ok(toDto(metadata))
    }

    @GetMapping("/{fileId}")
    fun getFileInfo(@PathVariable fileId: Long): ResponseEntity<FileResponseDto> {
        val metadata = fileService.getFileMetadata(fileId)
        return ResponseEntity.ok(toDto(metadata))
    }

    @DeleteMapping("/{fileId}")
    fun deleteFile(@PathVariable fileId: Long): ResponseEntity<Void> {
        val requesterId = UserContext.getRequiredUserId()
        fileService.deleteFile(fileId, requesterId)
        return ResponseEntity.noContent().build()
    }

    private fun toDto(metadata: FileMetadata): FileResponseDto {
        return FileResponseDto(
            id = metadata.id,
            fileName = metadata.fileName,
            originalFileName = metadata.originalFileName,
            s3Url = metadata.s3Url,
            fileSize = metadata.fileSize,
            contentType = metadata.contentType,
            uploaderId = metadata.uploaderId,
            createdAt = metadata.createdAt
        )
    }
}
