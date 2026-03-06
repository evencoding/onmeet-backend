package com.onmeet.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication



import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestTemplate
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories

@EnableFeignClients(basePackages = ["com.onmeet.auth", "com.onmeet.common.client"])
@EnableJpaRepositories(basePackages = ["com.onmeet.auth.repository.jpa"])
@EnableRedisRepositories(basePackages = ["com.onmeet.auth.repository.redis"])
@SpringBootApplication
class AuthApplication

fun main(args: Array<String>) {
    runApplication<AuthApplication>(*args)
}
