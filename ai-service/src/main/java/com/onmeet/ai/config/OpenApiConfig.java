package com.onmeet.ai.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "OnMeet AI Service API",
        version = "1.0",
        description = "회의록 생성, STT, 요약 등 AI 관련 API"
    ),
    servers = {
        @Server(url = "https://api.onmeet.cloud/ai", description = "Production Server"),
        @Server(url = "http://localhost:8080/ai", description = "Local Gateway Server"),
        @Server(url = "http://localhost:8082/ai", description = "Local Direct Server")
    },
    security = @SecurityRequirement(name = "Bearer Authentication")
)
@SecurityScheme(
    name = "Bearer Authentication",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer"
)
public class OpenApiConfig {
}
