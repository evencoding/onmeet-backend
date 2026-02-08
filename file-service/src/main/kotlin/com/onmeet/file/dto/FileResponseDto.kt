package com.onmeet.file.dto

import java.time.LocalDateTime

data class FileResponseDto(
    val id: Long?,
    val fileName: String,
    val originalFileName: String,
    val s3Url: String,
    val fileSize: Long,
    val contentType: String,
    val uploaderId: Long,
    val createdAt: LocalDateTime?
)
