package com.onmeet.file.service

import com.onmeet.common.exception.EntityNotFoundException
import com.onmeet.common.exception.InsufficientPermissionException
import com.onmeet.file.client.AuthServiceClient
import com.onmeet.file.dto.FileResponseDto
import com.onmeet.file.entity.FileMetadata
import com.onmeet.file.exception.*
import com.onmeet.file.service.FileEventProducer
import com.onmeet.file.repository.FileMetadataRepository
import com.onmeet.file.service.S3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class FileService(
    private val s3Service: S3Service,
    private val fileMetadataRepository: FileMetadataRepository,
    private val fileEventProducer: FileEventProducer,
    private val authServiceClient: com.onmeet.file.client.AuthServiceClient
) {
    private val log = LoggerFactory.getLogger(FileService::class.java)

    @org.springframework.beans.factory.annotation.Value("\${spring.cloud.aws.cloudfront.domain}")
    private lateinit var cloudFrontDomain: String

    @org.springframework.scheduling.annotation.Async
    @Transactional
    fun uploadFilesAsync(
        files: List<Triple<ByteArray, String, String>>,
        category: String,
        uploaderId: Long?,
        ownerType: String?,
        ownerId: String?,
        callbackTopic: String?,
        correlationId: String?
    ) = files.forEach { (fileBytes, originalFileName, contentType) ->
        runCatching {
            processFileUpload(
                inputStream = java.io.ByteArrayInputStream(fileBytes),
                fileSize = fileBytes.size.toLong(),
                originalFileName = originalFileName,
                contentType = contentType,
                category = category,
                uploaderId = uploaderId,
                ownerType = ownerType ?: "USER",
                ownerId = ownerId ?: uploaderId?.toString() ?: "SYSTEM"
            )
        }.onSuccess { metadata ->
            fileEventProducer.sendFileUploadEvent(
                topic = callbackTopic ?: "file-upload-events",
                fileId = metadata.id!!,
                fileName = metadata.fileName,
                fileUrl = metadata.s3Url,
                uploaderId = metadata.uploaderId ?: 0L,
                correlationId = correlationId
            )
            log.info("Async upload completed for file: {}", metadata.fileName)
        }.onFailure { e ->
            log.error("Async upload failed for file: {} in category: {}", originalFileName, category, e)
        }
    }

    @Transactional
    fun uploadFiles(
        files: List<MultipartFile>,
        category: String,
        uploaderId: Long?,
        ownerType: String?,
        ownerId: String?
    ): List<FileMetadata> = files.map { file ->
        runCatching {
            processFileUpload(
                inputStream = file.inputStream,
                fileSize = file.size,
                originalFileName = file.originalFilename ?: "unknown",
                contentType = file.contentType ?: "application/octet-stream",
                category = category,
                uploaderId = uploaderId,
                ownerType = ownerType ?: "USER",
                ownerId = ownerId ?: uploaderId?.toString() ?: "SYSTEM"
            )
        }.getOrElse { throw FileUploadException("Failed to upload file: ${file.originalFilename}", it) }
    }

    private fun processFileUpload(
        inputStream: java.io.InputStream,
        fileSize: Long,
        originalFileName: String,
        contentType: String,
        category: String,
        uploaderId: Long?,
        ownerType: String,
        ownerId: String
    ): FileMetadata {
        val extension = getFileExtension(originalFileName)
        val fileName = "${UUID.randomUUID()}$extension"
        val savedKey = "$ownerType/$ownerId/$category/$fileName"

        return try {
            s3Service.uploadFile(savedKey, inputStream, contentType)
            val metadata = FileMetadata(
                fileName = fileName,
                category = category,
                originalFileName = originalFileName,
                s3Url = "https://$cloudFrontDomain/$savedKey",
                fileSize = fileSize,
                contentType = contentType,
                ownerType = ownerType,
                ownerId = ownerId,
                uploaderId = uploaderId
            )
            fileMetadataRepository.save(metadata)
        } catch (e: Exception) {
            runCatching { s3Service.deleteFile(savedKey) }
                .onSuccess { log.info("Rolled back S3 upload for key: {}", savedKey) }
                .onFailure { log.error("Failed to rollback S3 upload for key: {}", savedKey, it) }
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getFileMetadata(fileId: Long): FileMetadata = fileMetadataRepository.findById(fileId)
        .orElseThrow { EntityNotFoundException("File not found with id: $fileId") }

    @Transactional
    fun deleteFile(fileId: Long, requesterId: Long) = getFileMetadata(fileId).let { metadata ->
        validateFileDeletePermission(metadata, requesterId)
        s3Service.deleteFile(metadata.fileName)
        fileMetadataRepository.delete(metadata)
    }

    private fun validateFileDeletePermission(metadata: FileMetadata, requesterId: Long) {
        val permissions = authServiceClient.getUserPermissions(requesterId)
            ?: throw InsufficientPermissionException("Could not verify user permissions. Permission check failed.")

        // 1. Admin: Full access
        if (permissions.roles.contains("ADMIN")) return

        // 2. Manager: Company-wide access (if file belongs to their company)
        if (permissions.roles.contains("MANAGER")) {
            if (metadata.companyIdMatches(permissions.companyId)) return
        }
        
        // 3. Uploader: If the requester is the uploader, they can delete their own files 
        // regardless of whether it's personal or team ownership.
        if (metadata.uploaderId == requesterId) return

        throw InsufficientPermissionException("You do not have permission to delete this file.")
    }

    private fun FileMetadata.companyIdMatches(companyId: Long?): Boolean =
        ownerType == "COMPANY" && ownerId == companyId?.toString()

    private fun getFileExtension(fileName: String): String =
        fileName.lastIndexOf('.').let { if (it == -1) "" else fileName.substring(it) }
}
