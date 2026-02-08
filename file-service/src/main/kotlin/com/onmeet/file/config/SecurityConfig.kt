package com.onmeet.file.config

import com.onmeet.common.security.GatewayPreAuthFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun gatewayPreAuthFilter(@Value("\${gateway.shared-secret}") gatewaySharedSecret: String): GatewayPreAuthFilter {
        return GatewayPreAuthFilter(gatewaySharedSecret)
    }

    @Bean
    fun filterChain(http: HttpSecurity, gatewayPreAuthFilter: GatewayPreAuthFilter): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(gatewayPreAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/image/actuator/**").permitAll()
                    .anyRequest().authenticated()
            }

        return http.build()
    }
}
