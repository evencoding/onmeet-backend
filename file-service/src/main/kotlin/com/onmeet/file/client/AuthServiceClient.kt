package com.onmeet.file.client

import com.onmeet.common.client.BaseServiceClient
import com.onmeet.file.dto.AuthPermissionResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class AuthServiceClient(
    restTemplate: RestTemplate,
    @Value("\${onmeet.auth.internal-url}")
    private val authInternalUrl: String
) : BaseServiceClient(restTemplate) {

    fun getUserPermissions(userId: Long): AuthPermissionResponse? =
        getWithAuth("$authInternalUrl/users/internal/$userId/permissions", AuthPermissionResponse::class.java)
}
