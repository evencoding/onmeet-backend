package com.onmeet.common.dto

data class SecurityCheckResponse(
    val authorized: Boolean,
    val message: String? = null
)
