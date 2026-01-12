package com.onmeet.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    public String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.replace("Bearer ", "").trim();
    }

    public boolean validateToken(String token) {
        return false;
    }

    public Authentication getAuthentication(String token) {
        return new UsernamePasswordAuthenticationToken(null, null, null);
    }
}
