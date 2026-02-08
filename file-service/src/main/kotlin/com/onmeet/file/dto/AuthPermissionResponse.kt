package com.onmeet.file.dto

data class AuthPermissionResponse(
    val userId: Long,
    val roles: Set<String>,
    val companyId: Long?,
    val teamIds: List<Long>
)
