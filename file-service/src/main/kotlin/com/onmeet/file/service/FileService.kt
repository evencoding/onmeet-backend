package com.onmeet.file.service

import com.onmeet.common.exception.EntityNotFoundException
import com.onmeet.common.exception.InsufficientPermissionException
import com.onmeet.file.entity.FileMetadata
import com.onmeet.file.exception.FileUploadException
import com.onmeet.file.repository.FileMetadataRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class FileService(
    private val s3Service: S3Service,
    private val fileMetadataRepository: FileMetadataRepository
) {
    private val log = LoggerFactory.getLogger(FileService::class.java)

    @Transactional
    fun uploadFile(file: MultipartFile, uploaderId: Long): FileMetadata {
        var savedFileName: String? = null
        try {
            val originalFileName = file.originalFilename ?: ""
            val extension = getFileExtension(originalFileName)
            savedFileName = UUID.randomUUID().toString() + extension

            val s3Url = s3Service.uploadFile(savedFileName, file.inputStream, file.contentType ?: "application/octet-stream")

            val metadata = FileMetadata(
                fileName = savedFileName,
                originalFileName = originalFileName,
                s3Url = s3Url,
                fileSize = file.size,
                contentType = file.contentType ?: "application/octet-stream",
                uploaderId = uploaderId
            )

            return fileMetadataRepository.save(metadata)
        } catch (e: Exception) {
            if (savedFileName != null) {
                try {
                    s3Service.deleteFile(savedFileName)
                    log.info("Rolled back S3 upload for file: {}", savedFileName)
                } catch (deleteEx: Exception) {
                    log.error("Failed to rollback S3 upload for file: {}", savedFileName, deleteEx)
                }
            }
            throw FileUploadException("Failed to upload file", e)
        }
    }

    @Transactional(readOnly = true)
    fun getFileMetadata(fileId: Long): FileMetadata {
        return fileMetadataRepository.findById(fileId)
            .orElseThrow { EntityNotFoundException("File not found with id: $fileId") }
    }

    @Transactional
    fun deleteFile(fileId: Long, requesterId: Long) {
        val metadata = getFileMetadata(fileId)
        validateFileOwner(metadata, requesterId)

        s3Service.deleteFile(metadata.fileName)
        fileMetadataRepository.delete(metadata)
    }

    private fun validateFileOwner(metadata: FileMetadata, requesterId: Long) {
        if (metadata.uploaderId != requesterId) {
            throw InsufficientPermissionException("You do not have permission to delete this file.")
        }
    }

    private fun getFileExtension(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex == -1) "" else fileName.substring(dotIndex)
    }
}
