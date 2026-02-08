package com.onmeet.common.security

object JwtConstants {
    const val AUTHORIZATION_HEADER = "Authorization"
    const val BEARER_PREFIX = "Bearer "
    const val ACCESS_TOKEN_COOKIE_NAME = "accessToken"
    const val REFRESH_TOKEN_COOKIE_NAME = "refreshToken"
    const val USER_ID_CLAIM = "userId"
    const val ROLE_CLAIM = "role"
    const val CLAIM_AUTHORITIES = "authorities"
    const val MEETING_ID_CLAIM = "meetingId"
}
