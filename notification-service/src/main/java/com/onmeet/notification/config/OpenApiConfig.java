package com.onmeet.notification.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "OnMeet Notification Service API",
        version = "1.0",
        description = "알림 관리 API"
    ),
    servers = {
        @Server(url = "https://api.onmeet.cloud/notification", description = "Production Server"),
        @Server(url = "http://localhost:8080/notification", description = "Local Gateway Server"),
        @Server(url = "http://localhost:8085/notification", description = "Local Direct Server")
    }
)
public class OpenApiConfig {
}
