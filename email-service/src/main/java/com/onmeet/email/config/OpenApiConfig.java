package com.onmeet.email.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "OnMeet Email Service API",
        version = "1.0",
        description = "이메일 발송 API"
    ),
    servers = {
        @Server(url = "https://api.onmeet.cloud/email", description = "Production Server"),
        @Server(url = "http://localhost:8080/email", description = "Local Gateway Server"),
        @Server(url = "http://localhost:8087/email", description = "Local Direct Server")
    }
)
public class OpenApiConfig {
}
