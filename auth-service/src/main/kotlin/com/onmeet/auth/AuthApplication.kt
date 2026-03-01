package com.onmeet.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication



import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestTemplate

@SpringBootApplication
class AuthApplication

fun main(args: Array<String>) {
    runApplication<AuthApplication>(*args)
}
