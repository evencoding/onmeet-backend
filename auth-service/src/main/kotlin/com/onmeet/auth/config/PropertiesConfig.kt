package com.onmeet.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    AuthProperties::class,
    GatewayProperties::class,
    JwtProperties::class,
    InvitationProperties::class,
    TeamProperties::class
)
class PropertiesConfig

@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    val encryptionKey: String = ""
)

@ConfigurationProperties(prefix = "gateway")
data class GatewayProperties(
    val sharedSecret: String = ""
)

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val validityInMs: Long = 3600000,
    val keyId: String = "",
    val cookie: CookieProperties = CookieProperties(),
    val refreshCookie: RefreshCookieProperties = RefreshCookieProperties()
) {
    data class CookieProperties(
        val secure: Boolean = true,
        val maxAge: Long = 3600
    )

    data class RefreshCookieProperties(
        val maxAge: Long = 604800
    )
}

@ConfigurationProperties(prefix = "invitation")
data class InvitationProperties(
    val expiryDays: Long = 7
)

@ConfigurationProperties(prefix = "app.team")
data class TeamProperties(
    val initialColor: String = "#FFFFFF",
    val initialDescription: String = "Initial team"
)
