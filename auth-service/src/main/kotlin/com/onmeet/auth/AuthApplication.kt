package com.onmeet.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication



import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestTemplate

@EnableFeignClients(basePackages = ["com.onmeet.auth", "com.onmeet.common.client"])
@SpringBootApplication
class AuthApplication

fun main(args: Array<String>) {
    runApplication<AuthApplication>(*args)
}
