package com.onmeet.video.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "OnMeet Video Service API",
        version = "1.0",
        description = "화상 회의, 참가자 관리, 녹화 및 화면 공유 API"
    ),
    servers = {
        @Server(url = "https://api.onmeet.cloud/video", description = "Production Server"),
        @Server(url = "http://localhost:8080/video", description = "Local Gateway Server"),
        @Server(url = "http://localhost:8083/video", description = "Local Direct Server")
    }
)
public class OpenApiConfig {
}
