package com.onmeet.notification.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.onmeet.common.security.GatewayPreAuthFilter;

/**
 * Notification 서비스 보안 설정
 * - Swagger 경로(permitAll)는 common-security의 CommonSwaggerSecurityConfig에서 처리
 * - context-path: /notification
 * - SSE 구독 엔드포인트(/notification/v1/sse/subscribe)는 인증 헤더(X-User-Id)로 처리되므로 인증 필요
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public GatewayPreAuthFilter gatewayPreAuthFilter(@Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return new GatewayPreAuthFilter(gatewaySharedSecret);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:8080",
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://onmeet.cloud",
                "https://api.onmeet.cloud",
                "https://*.onmeet.cloud"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayPreAuthFilter gatewayPreAuthFilter)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(gatewayPreAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Actuator 엔드포인트 허용 (context-path: /notification)
                        .requestMatchers("/actuator/**").permitAll()
                        // 나머지 모든 요청은 인증 필요
                        .anyRequest().authenticated());

        return http.build();
    }
}

