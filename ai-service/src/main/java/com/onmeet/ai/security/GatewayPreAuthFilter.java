package com.onmeet.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GatewayPreAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
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
