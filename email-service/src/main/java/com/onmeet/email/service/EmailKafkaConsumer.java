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

    /*
     * [개발 팀 공유 사항]
     * Kafka 토픽 리스너입니다.
     *
     * 1. 'email-send-topic'에서 메시지를 수신합니다.
     * 2. 수신된 JSON 메시지를 `EmailRequestDto`로 변환합니다.
     * 3. 변환된 데이터를 `EmailService.sendEmail`로 전달하여 메일 발송 로직을 실행합니다.
     *
     * 디버깅 팁:
     * - 메시지가 수신되지 않는 경우 Docker 컨테이너의 Kafka가 정상 작동 중인지 확인하세요.
     * - `groupId`가 'email-service-group'으로 설정되어 있어, 동일 그룹 내의 다른 인스턴스와 부하 분산됩니다.
     */
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
