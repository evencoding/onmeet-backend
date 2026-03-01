package com.onmeet.common.security

import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.*

object UserContext {
    const val USER_ID_HEADER = "X-User-Id"
    const val USER_EMAIL_HEADER = "X-User-Email"
    const val USER_ROLES_HEADER = "X-User-Roles"

    @JvmStatic
    fun getUserId(): Optional<Long> {
        return getHeader(USER_ID_HEADER).map { it.toLong() }
    }

    @JvmStatic
    fun getRequiredUserId(): Long {
        return getUserId().orElseThrow { IllegalStateException("User ID is missing from request headers") }
    }

    @JvmStatic
    fun getUserEmail(): Optional<String> {
        return getHeader(USER_EMAIL_HEADER)
    }

    @JvmStatic
    fun getUserRoles(): Optional<String> {
        return getHeader(USER_ROLES_HEADER)
    }

    private fun getHeader(headerName: String): Optional<String> {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        return Optional.ofNullable(attributes?.request?.getHeader(headerName))
    }
}
