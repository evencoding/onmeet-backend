package com.onmeet.email.service;

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
        String message = "{\"to\":\"test@example.com\",\"subject\":\"Subject\",\"body\":\"Body\"}";
        EmailRequestDto dto = new EmailRequestDto();
        dto.setTo("test@example.com");
        dto.setSubject("Subject");
        dto.setBody("Body");

        when(objectMapper.readValue(message, EmailRequestDto.class)).thenReturn(dto);

        // When
        emailKafkaConsumer.consume(message);

        // Then
        verify(emailService).sendEmail("test@example.com", "Subject", "Body");
    }
}
