package com.onmeet.common.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorDetail? = null
) {
    data class ErrorDetail(
        val code: String? = null,
        val status: Int,
        val message: String
    )

    companion object {
        fun <T> success(data: T?): ApiResponse<T> = ApiResponse(success = true, data = data)

        fun <T> error(status: Int, message: String, code: String? = null): ApiResponse<T> =
            ApiResponse(success = false, error = ErrorDetail(code, status, message))
    }
}
