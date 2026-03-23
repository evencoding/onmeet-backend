package com.onmeet.auth.config

import com.onmeet.auth.security.AuthGatewayPreAuthFilter
import com.onmeet.auth.security.GatewaySecretFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

import com.onmeet.auth.security.JwtAuthenticationFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val authGatewayPreAuthFilter: AuthGatewayPreAuthFilter,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val gatewaySecretFilter: GatewaySecretFilter
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager {
        return authenticationConfiguration.authenticationManager
    }

    // GatewaySecretFilter는 Security Filter Chain 내에서만 사용되어야 하므로
    // 서블릿 컨테이너의 자동 필터 등록을 비활성화한다.
    // 자동 등록되면 모든 요청에 대해 Security Chain 바깥에서도 실행되어
    // JWKS 등 공개 엔드포인트까지 차단하는 문제가 발생한다.
    @Bean
    fun disableGatewaySecretFilterAutoRegistration(): FilterRegistrationBean<GatewaySecretFilter> {
        val registration = FilterRegistrationBean(gatewaySecretFilter)
        registration.isEnabled = false
        return registration
    }

    // AuthGatewayPreAuthFilter도 동일하게 자동 등록 비활성화
    @Bean
    fun disableAuthGatewayPreAuthFilterAutoRegistration(): FilterRegistrationBean<AuthGatewayPreAuthFilter> {
        val registration = FilterRegistrationBean(authGatewayPreAuthFilter)
        registration.isEnabled = false
        return registration
    }

    // JwtAuthenticationFilter도 동일하게 자동 등록 비활성화
    @Bean
    fun disableJwtAuthFilterAutoRegistration(): FilterRegistrationBean<JwtAuthenticationFilter> {
        val registration = FilterRegistrationBean(jwtAuthenticationFilter)
        registration.isEnabled = false
        return registration
    }

    @Bean
    @Order(0)
    fun internalApiFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/internal/**")
            .cors { it.disable() }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .addFilterBefore(gatewaySecretFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.disable() }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/v1/register/**",
                    "/v1/login/**",
                    "/v1/invitations/**",
                    "/v1/check",
                    "/v1/refresh",
                    "/v1/logout",
                    "/actuator/**",
                    "/v1/.well-known/jwks.json"
                ).permitAll()
                auth.requestMatchers("/v1/manager/**").hasAnyRole("MANAGER", "ADMIN")
                auth.anyRequest().authenticated()
            }
            .addFilterBefore(authGatewayPreAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, authGatewayPreAuthFilter::class.java)

        return http.build()
    }

}
