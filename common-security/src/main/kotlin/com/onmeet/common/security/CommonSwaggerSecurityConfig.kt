package com.onmeet.common.security

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@AutoConfiguration
@ConditionalOnClass(HttpSecurity::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CommonSwaggerSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun swaggerFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(
                "/v3/api-docs/**",
                "/v1/v3/api-docs/**",
                "/v1/v3/api-docs",
                "/swagger-ui/**",
                "/swagger-ui.html"
            )
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        
        return http.build()
    }
}
