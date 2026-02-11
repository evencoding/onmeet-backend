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
    @Value("\${onmeet.file.internal-url}") private val fileServiceUrl: String
) : BaseServiceClient(restTemplate) {

    /**
     * 기본 프로필 이미지를 생성 요청합니다.
     */
    fun generateDefaultProfileImage(name: String): FileMetadataResponse? {
        val url = "$fileServiceUrl/file/profile/default"
        val request = mapOf(
            "name" to name,
            "ownerType" to "USER"
        )
        return postWithAuth(url, request, FileMetadataResponse::class.java)
    }

    /**
     * 프로필 이미지를 업로드합니다.
     */
    fun uploadProfileImage(file: MultipartFile, ownerId: String): FileMetadataResponse? {
        val url = "$fileServiceUrl/file/upload"
        
        val body = LinkedMultiValueMap<String, Any>()
        body.add("files", file.resource)
        body.add("category", "profile")
        body.add("ownerType", "USER")
        body.add("ownerId", ownerId)

        val results = postMultipartWithAuth(url, body, Array<FileMetadataResponse>::class.java)
        return results?.firstOrNull()
    }

    /**
     * 파일을 삭제합니다.
     */
    fun deleteFile(fileId: Long) {
        val url = "$fileServiceUrl/file/$fileId"
        restTemplate.delete(url)
    }
}
