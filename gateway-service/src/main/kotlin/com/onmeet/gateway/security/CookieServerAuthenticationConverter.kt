package com.onmeet.gateway.security

import org.springframework.http.HttpCookie
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class CookieServerAuthenticationConverter : ServerAuthenticationConverter {

    override fun convert(exchange: ServerWebExchange): Mono<Authentication> {
        return Mono.justOrEmpty(exchange.request.cookies.getFirst("accessToken"))
            .map { cookie: HttpCookie -> cookie.value }
            .map { token: String -> BearerTokenAuthenticationToken(token) }
    }
}
