package com.onmeet.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
class AuthApplication

fun main(args: Array<String>) {
    runApplication<AuthApplication>(*args)
}
