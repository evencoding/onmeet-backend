package com.onmeet.video.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.onmeet.common.security.GatewayPreAuthFilter;

@Configuration
@EnableWebSecurity
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

    @Bean
    @Order(0)
    public SecurityFilterChain chatFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/chat-test", "/chat-test/**", "/ws-chat/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http, GatewayPreAuthFilter gatewayPreAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(gatewayPreAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/video/actuator/**").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
