package com.onmeet.gateway.config

import com.onmeet.gateway.security.CookieServerAuthenticationConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val cookieServerAuthenticationConverter: CookieServerAuthenticationConverter
) {

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(ServerCsrfTokenRequestAttributeHandler())
            }
            .authorizeExchange { exchanges ->
                exchanges.pathMatchers("/auth/**", "/.well-known/**").permitAll()
                exchanges.anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
                oauth2.bearerTokenConverter(cookieServerAuthenticationConverter)
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }

        return http.build()
    }
}
