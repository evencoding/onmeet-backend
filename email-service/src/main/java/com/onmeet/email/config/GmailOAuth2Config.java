package com.onmeet.email.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gmail.oauth2")
@Getter
@Setter
public class GmailOAuth2Config {

    private String clientId;
    private String clientSecret;
    private String refreshToken;
}
