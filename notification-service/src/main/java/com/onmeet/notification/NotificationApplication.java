package com.onmeet.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@org.springframework.data.jpa.repository.config.EnableJpaAuditing
@org.springframework.scheduling.annotation.EnableScheduling
@EnableRetry
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }

}
