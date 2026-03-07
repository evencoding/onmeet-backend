package com.onmeet.email.service;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.email.dto.EmailRequestDto;

@ExtendWith(MockitoExtension.class)
class EmailKafkaConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EmailKafkaConsumer emailKafkaConsumer;

    @Test
    void consume_ShouldParseMessageAndCallSendEmail() throws JsonProcessingException {
        // Given
        String message = "{\"to\":\"test@example.com\",\"subject\":\"Subject\",\"templateName\":\"guest-invitation\",\"variables\":{}}";
        Map<String, Object> variables = Map.of();
        EmailRequestDto dto = new EmailRequestDto("test@example.com", "Subject", "guest-invitation", variables);

        when(objectMapper.readValue(message, EmailRequestDto.class)).thenReturn(dto);

        // When
        emailKafkaConsumer.consume(message);

        // Then
        verify(emailService).sendEmail("test@example.com", "Subject", "guest-invitation", variables);
    }
}
