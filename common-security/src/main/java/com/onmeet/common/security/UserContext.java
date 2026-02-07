package com.onmeet.common.security;

import java.util.Optional;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public final class UserContext {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    private UserContext() {
    }

    public static Optional<Long> getUserId() {
        return getHeader(USER_ID_HEADER).map(Long::parseLong);
    }

    public static Long getRequiredUserId() {
        return getUserId().orElseThrow(() -> new IllegalStateException("User ID is missing from request headers"));
    }

    public static Optional<String> getUserEmail() {
        return getHeader(USER_EMAIL_HEADER);
    }

    public static Optional<String> getUserRoles() {
        return getHeader(USER_ROLES_HEADER);
    }

    private static Optional<String> getHeader(String headerName) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return Optional.ofNullable(request.getHeader(headerName));
        }
        return Optional.empty();
    }
}
