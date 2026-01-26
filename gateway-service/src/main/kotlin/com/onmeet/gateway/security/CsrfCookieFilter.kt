package com.onmeet.gateway.security

import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class CsrfCookieFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val csrfTokenProxy = exchange.getAttribute<Mono<CsrfToken>>(CsrfToken::class.java.name) ?: Mono.empty()
        
        return csrfTokenProxy.doOnSuccess { token -> 
            exchange.response.headers.add("X-CSRF-TOKEN", token.token)
        }
            .then(chain.filter(exchange))
    }
}
