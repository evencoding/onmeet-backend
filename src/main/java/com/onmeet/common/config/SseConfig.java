package com.onmeet.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SseConfig {

    @Bean
    public Long sseTimeoutMillis() {
        return 30 * 60 * 1000L;
    }
}
