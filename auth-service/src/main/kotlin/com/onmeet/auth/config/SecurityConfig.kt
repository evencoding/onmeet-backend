package com.onmeet.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import com.onmeet.auth.security.JwtAuthenticationFilter
import com.onmeet.auth.security.GatewayPreAuthFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager {
        return authenticationConfiguration.authenticationManager
    }

    @Bean
    fun filterChain(http: HttpSecurity, gatewayPreAuthFilter: GatewayPreAuthFilter): SecurityFilterChain {
        http
            .csrf { it.disable() } // Using JWT, CSRF disabled (stateless)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/auth/login", "/auth/signup", "/auth/check", "/.well-known/**", "/auth/actuator/**", "/error").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(gatewayPreAuthFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter::class.java)
        
        return http.build()
    }
}
