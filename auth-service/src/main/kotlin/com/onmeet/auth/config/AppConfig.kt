package com.onmeet.auth.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate

@Configuration
@EnableScheduling
@EnableCaching
class AppConfig {

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }
}
