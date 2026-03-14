package com.onmeet.gateway.config

import com.onmeet.gateway.security.CookieServerAuthenticationConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter

import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val cookieServerAuthenticationConverter: CookieServerAuthenticationConverter,
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") private val jwkSetUri: String
) {

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeExchange { exchanges ->
                // Public endpoints - Authentication
                exchanges.pathMatchers(
                    "/auth/v1/register/**",
                    "/auth/v1/login/**",
                    "/auth/v1/invitations/validate",
                    "/auth/v1/check",
                    "/auth/v1/refresh",
                    "/auth/v1/logout",
                    "/.well-known/**",
                    "/ws-chat",
                    "/ws-chat/**"
                ).permitAll()

                // Public endpoints - Infrastructure (Actuator: only health and info)
                exchanges.pathMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/*/actuator/health",
                    "/*/actuator/info",
                    "/file/actuator/health",
                    "/file/actuator/info",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/*/v3/api-docs",
                    "/*/v1/v3/api-docs",
                    "/file/doc.json",
                    "/file/swagger/**",
                    "/error"
                ).permitAll()

                // All other requests require authentication
                exchanges.anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
                oauth2.bearerTokenConverter(cookieServerAuthenticationConverter)
            }
            .headers { headers ->
                headers.frameOptions { frameOptions ->
                    frameOptions.mode(XFrameOptionsServerHttpHeadersWriter.Mode.SAMEORIGIN)
                }
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf(
            "http://localhost:8080", 
            "http://localhost:*", 
            "http://127.0.0.1:*", 
            "https://onmeet.cloud",
            "https://api.onmeet.cloud", 
            "https://*.onmeet.cloud"
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
        configuration.allowedHeaders = listOf("*")
        configuration.exposedHeaders = listOf("Content-Disposition")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build()
    }

    @Bean
    fun jwtAuthenticationConverter(): ReactiveJwtAuthenticationConverter {
        val jwtGrantedAuthoritiesConverter = JwtGrantedAuthoritiesConverter()
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("")
        jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("role")

        val converter = ReactiveJwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter(
            ReactiveJwtGrantedAuthoritiesConverterAdapter(jwtGrantedAuthoritiesConverter)
        )
        return converter
    }
}
