package com.onmeet.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final ObjectMapper objectMapper;
    private final String secret;
    private final long expirationSeconds;

    public JwtTokenProvider(
        ObjectMapper objectMapper,
        @Value("${app.jwt.secret:change-me}") String secret,
        @Value("${app.jwt.expiration-seconds:3600}") long expirationSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
    }

    public String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.replace("Bearer ", "").trim();
    }

    public String createToken(User user) {
        long now = Instant.now().getEpochSecond();
        long exp = now + expirationSeconds;
        Map<String, Object> payload = Map.of(
            "sub", user.getId(),
            "email", user.getEmail(),
            "name", user.getName(),
            "iat", now,
            "exp", exp
        );
        return encode(payload);
    }

    public boolean validateToken(String token) {
        try {
            Map<String, Object> payload = decode(token);
            Object expValue = payload.get("exp");
            if (expValue == null) {
                return false;
            }
            long exp = ((Number) expValue).longValue();
            return Instant.now().getEpochSecond() < exp;
        } catch (Exception ex) {
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Map<String, Object> payload = decode(token);
        String userId = String.valueOf(payload.get("sub"));
        String email = String.valueOf(payload.get("email"));
        String name = String.valueOf(payload.get("name"));
        AuthUserPrincipal principal = new AuthUserPrincipal(userId, email, name);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private String encode(Map<String, Object> payload) {
        try {
            String headerJson = objectMapper.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"));
            String payloadJson = objectMapper.writeValueAsString(payload);
            String header = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
            String body = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = sign(header + "." + body);
            return header + "." + body + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create token", ex);
        }
    }

    private Map<String, Object> decode(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token");
            }
            String unsigned = parts[0] + "." + parts[1];
            String expectedSignature = sign(unsigned);
            if (!constantTimeEquals(parts[2], expectedSignature)) {
                throw new IllegalArgumentException("Invalid signature");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(decoded, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid token", ex);
        }
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(raw);
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
