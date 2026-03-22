package com.onmeet.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication



import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestTemplate
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

@EnableFeignClients(basePackages = ["com.onmeet.auth", "com.onmeet.common.client"])
@EnableJpaRepositories(
    basePackages = ["com.onmeet.auth.repository.jpa"],
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASPECTJ, pattern = ["com.onmeet.auth.repository.redis.*"])]
)
@EnableRedisRepositories(
    basePackages = ["com.onmeet.auth.repository.redis"],
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASPECTJ, pattern = ["com.onmeet.auth.repository.jpa.*"])]
)
@SpringBootApplication
class AuthApplication

fun main(args: Array<String>) {
    runApplication<AuthApplication>(*args)
}
