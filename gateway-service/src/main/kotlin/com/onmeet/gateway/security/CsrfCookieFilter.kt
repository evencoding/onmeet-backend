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
        val csrfToken: Mono<CsrfToken> = exchange.getAttribute<Mono<CsrfToken>>(CsrfToken::class.java.name) ?: Mono.empty()
        
        return csrfToken.doOnNext { _ ->
            // Subscribing to CsrfToken ensures it's generated and added to the response cookie by the repository
        }.then(chain.filter(exchange))
    }
}
