package com.onmeet.video.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.onmeet.common.security.GatewayPreAuthFilter;

/**
 * Video 서비스 보안 설정
 * - Swagger 경로(permitAll)는 common-security의 CommonSwaggerSecurityConfig에서 처리
 * - context-path: /video
 *
 * 공개(permit) 엔드포인트:
 *   - /actuator/** : 헬스체크 및 메트릭
 *   - /webhook/** : LiveKit 이벤트 웹훅 (외부 LiveKit 서버에서 호출)
 *   - /chat-test, /ws-chat/** : 웹소켓 테스트 및 연결 (인증 없이 접근 가능해야 함)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public GatewayPreAuthFilter gatewayPreAuthFilter(@Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return new GatewayPreAuthFilter(gatewaySharedSecret);
    }

    @Bean
    public FilterRegistrationBean<GatewayPreAuthFilter> gatewayPreAuthFilterRegistration(GatewayPreAuthFilter filter) {
        FilterRegistrationBean<GatewayPreAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * WebSocket 엔드포인트 체인 (최우선)
     * - 웹소켓 연결은 Spring Security 필터를 통과하지 않도록 허용
     */
    @Bean
    @Order(0)
    public SecurityFilterChain websocketFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/chat-test", "/chat-test/**", "/ws-chat/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    /**
     * 일반 API 엔드포인트 체인
     */
    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayPreAuthFilter gatewayPreAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(gatewayPreAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Actuator 엔드포인트 허용 (context-path: /video)
                        .requestMatchers("/actuator/**").permitAll()
                        // LiveKit 웹훅 허용 (외부 LiveKit 서버에서 서명된 요청을 보냄)
                        .requestMatchers("/webhook/**").permitAll()
                        // 나머지 모든 요청은 인증 필요
                        .anyRequest().authenticated());

        return http.build();
    }
}
