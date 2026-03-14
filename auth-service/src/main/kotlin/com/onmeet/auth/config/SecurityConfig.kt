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

    @Bean
    @Order(0)
    fun internalApiFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/v1/internal/**")
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
