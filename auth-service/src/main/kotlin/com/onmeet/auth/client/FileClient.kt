package com.onmeet.auth.client

import com.onmeet.common.client.BaseServiceClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile
import org.springframework.util.LinkedMultiValueMap
import org.springframework.core.io.Resource

data class FileMetadataResponse(
    val id: Long,
    val fileName: String,
    val s3Url: String,
    val contentType: String
)

@Service
class FileClient(
    restTemplate: RestTemplate,
    @Value("\${onmeet.file.internal-url}") private val fileServiceUrl: String,
    @Value("\${gateway.shared-secret}") gatewaySecret: String
) : BaseServiceClient(restTemplate, gatewaySecret) {

    /**
     * 기본 프로필 이미지를 생성 요청합니다.
     */
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "fileService", fallbackMethod = "generateDefaultProfileImageFallback")
    fun generateDefaultProfileImage(name: String): FileMetadataResponse? {
        val url = "$fileServiceUrl/file/profile/default"
        val request = mapOf(
            "name" to name,
            "ownerType" to "USER"
        )
        return postWithAuthOrThrow(url, request, FileMetadataResponse::class.java)
    }

    fun generateDefaultProfileImageFallback(name: String, t: Throwable): FileMetadataResponse? {
        log.error("Failed to generate default profile image for $name. Error: ${t.message}")
        return null
    }

    /**
     * 프로필 이미지를 업로드합니다.
     */
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "fileService", fallbackMethod = "uploadProfileImageFallback")
    fun uploadProfileImage(file: MultipartFile, ownerId: String): FileMetadataResponse? {
        val url = "$fileServiceUrl/file/upload"
        
        val body = LinkedMultiValueMap<String, Any>()
        body.add("files", file.resource)
        body.add("category", "profile")
        body.add("ownerType", "USER")
        body.add("ownerId", ownerId)

        val results = postMultipartWithAuthOrThrow(url, body, Array<FileMetadataResponse>::class.java)
        return results?.firstOrNull()
    }

    fun uploadProfileImageFallback(file: MultipartFile, ownerId: String, t: Throwable): FileMetadataResponse? {
        log.error("Failed to upload profile image for owner $ownerId. Error: ${t.message}")
        return null
    }

    /**
     * 파일을 삭제합니다.
     */
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "fileService", fallbackMethod = "deleteFileFallback")
    fun deleteFile(fileId: Long) {
        val url = "$fileServiceUrl/file/$fileId"
        deleteWithAuth(url)
    }

    fun deleteFileFallback(fileId: Long, t: Throwable) {
        log.error("Failed to delete file $fileId. Error: ${t.message}")
    }

    /**
     * 자신의 프로필 이미지를 삭제합니다.
     */
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "fileService", fallbackMethod = "deleteMyProfileImageFallback")
    fun deleteMyProfileImage() {
        val url = "$fileServiceUrl/file/me/profile"
        deleteWithAuth(url)
    }

    fun deleteMyProfileImageFallback(t: Throwable) {
        log.error("Failed to delete my profile image. Error: ${t.message}")
    }

    /**
     * 기존 프로필 이미지가 존재하는 경우 삭제를 시도합니다.
     * 삭제 실패 시 예외를 던지지 않고 경고 로그만 남깁니다.
     */
    fun safeDeleteProfileImageIfPresent(profileImageId: Long?, logContext: String) {
        if (profileImageId == null) return
        try {
            deleteFile(profileImageId)
        } catch (e: Exception) {
            log.warn("프로필 이미지 삭제 실패 ($logContext): ${e.message}")
        }
    }
}
