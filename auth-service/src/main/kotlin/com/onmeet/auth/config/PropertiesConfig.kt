package com.onmeet.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    AuthProperties::class,
    GatewayProperties::class,
    JwtProperties::class,
    InvitationProperties::class
)
class PropertiesConfig

@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    var encryptionKey: String = ""
)

@ConfigurationProperties(prefix = "gateway")
data class GatewayProperties(
    var sharedSecret: String = ""
)

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    var validityInMs: Long = 3600000,
    var keyId: String = "",
    var cookie: CookieProperties = CookieProperties(),
    var refreshCookie: RefreshCookieProperties = RefreshCookieProperties()
) {
    data class CookieProperties(
        var secure: Boolean = true,
        var maxAge: Long = 3600
    )

    data class RefreshCookieProperties(
        var maxAge: Long = 604800
    )
}

@ConfigurationProperties(prefix = "invitation")
data class InvitationProperties(
    var expiryDays: Long = 7
)
