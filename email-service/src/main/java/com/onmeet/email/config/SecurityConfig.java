package com.onmeet.email.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Email 서비스 보안 설정
 * - Swagger 경로(permitAll)는 common-security의 CommonSwaggerSecurityConfig에서 처리
 * - context-path: /email
 * - email-service는 Kafka 컨슈머(이벤트 드리븐)로 동작하므로, 외부 HTTP 요청은 최소화
 * - 헬스 체크(/v1/health)와 actuator는 허용
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Actuator 엔드포인트 허용 (application.yml의 management.endpoints.web.base-path 참조)
                        .requestMatchers("/actuator/**").permitAll()
                        // 헬스 체크 엔드포인트 허용
                        .requestMatchers("/v1/health").permitAll()
                        // 나머지 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
