package com.onmeet.email.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.email.dto.EmailRequestDto;

@Service
public class EmailKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailKafkaConsumer.class);

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public EmailKafkaConsumer(EmailService emailService, ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "email-send-topic", groupId = "email-service-group")
    public void consume(String message) {
        log.info("Received Kafka message: {}", message);
        try {
            EmailRequestDto emailRequest = objectMapper.readValue(message, EmailRequestDto.class);
            emailService.sendEmail(emailRequest.getTo(), emailRequest.getSubject(), emailRequest.getBody());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error processing Kafka message: {}", message, e);
        }
    }
}
