package com.onmeet.common.security;

public final class JwtConstants {
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    public static final String USER_ID_CLAIM = "userId";
    public static final String ROLE_CLAIM = "role";

    private JwtConstants() {
        // Private constructor to prevent instantiation
    }
}
