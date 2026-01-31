package com.onmeet.common.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GatewayPreAuthFilter extends OncePerRequestFilter {

    private final String gatewaySharedSecret;

    public GatewayPreAuthFilter(@Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        this.gatewaySharedSecret = gatewaySharedSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.contains("/actuator/") || path.startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Validate Gateway Shared Secret to prevent spoofing
        String gatewaySecret = request.getHeader("X-Gateway-Secret");
        if (gatewaySecret == null || !java.security.MessageDigest.isEqual(gatewaySecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), gatewaySharedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Gateway Secret");
            return;
        }

        String userId = request.getHeader("X-User-Id");
        String userRoles = request.getHeader("X-User-Roles");

        if (userId != null && !userId.isBlank()) {
            List<SimpleGrantedAuthority> authorities;
            if (userRoles != null && !userRoles.isBlank()) {
                authorities = Arrays.stream(userRoles.split(","))
                        .map(role -> new SimpleGrantedAuthority(role.trim()))
                        .collect(Collectors.toList());
            } else {
                authorities = Collections.emptyList();
            }

            Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
