package com.onmeet.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayPreAuthFilter gatewayPreAuthFilter)
            throws Exception {
        http
                .cors(cors -> cors.disable())
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
