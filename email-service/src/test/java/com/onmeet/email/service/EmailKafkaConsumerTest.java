package com.onmeet.email.service;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.EmailErrorCode;
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

    @Test
    void consume_WhenEmailServiceThrowsBusinessException_ShouldNotPropagate() throws JsonProcessingException {
        // Given: sendEmail throws BusinessException (e.g., invalid template, credentials missing)
        // The consumer must swallow it and log, not re-throw (otherwise Kafka offset won't commit)
        String message = "{\"to\":\"user@example.com\",\"subject\":\"Test\",\"templateName\":\"guest-invitation\",\"variables\":{}}";
        Map<String, Object> variables = Map.of();
        EmailRequestDto dto = new EmailRequestDto("user@example.com", "Test", "guest-invitation", variables);

        when(objectMapper.readValue(message, EmailRequestDto.class)).thenReturn(dto);
        doThrow(new BusinessException(EmailErrorCode.INVALID_TEMPLATE))
                .when(emailService).sendEmail("user@example.com", "Test", "guest-invitation", variables);

        // When & Then: must not throw
        assertDoesNotThrow(() -> emailKafkaConsumer.consume(message));
    }

    @Test
    void consume_WhenEmailServiceThrowsCredentialsMissing_ShouldNotPropagate() throws JsonProcessingException {
        // Given
        String message = "{\"to\":\"user@example.com\",\"subject\":\"Test\",\"templateName\":\"company-invitation\",\"variables\":{}}";
        Map<String, Object> variables = Map.of();
        EmailRequestDto dto = new EmailRequestDto("user@example.com", "Test", "company-invitation", variables);

        when(objectMapper.readValue(message, EmailRequestDto.class)).thenReturn(dto);
        doThrow(new BusinessException(EmailErrorCode.CREDENTIALS_MISSING))
                .when(emailService).sendEmail("user@example.com", "Test", "company-invitation", variables);

        // When & Then
        assertDoesNotThrow(() -> emailKafkaConsumer.consume(message));
    }

    @Test
    void consume_WhenJsonParsingFails_ShouldNotPropagate() throws JsonProcessingException {
        // Given: malformed JSON should not cause an unhandled exception
        String malformedMessage = "{{invalid-json}}";

        when(objectMapper.readValue(malformedMessage, EmailRequestDto.class))
                .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "unexpected token"));

        // When & Then: must not throw
        assertDoesNotThrow(() -> emailKafkaConsumer.consume(malformedMessage));

        // emailService must not be called on parse failure
        verify(emailService, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void consume_WhenEmailSendFails_ShouldNotPropagate() throws JsonProcessingException {
        // Given: SMTP failure (BusinessException.SEND_FAILED) must not bubble out of consumer
        String message = "{\"to\":\"user@example.com\",\"subject\":\"Test\",\"templateName\":\"temporary-password\",\"variables\":{}}";
        Map<String, Object> variables = Map.of();
        EmailRequestDto dto = new EmailRequestDto("user@example.com", "Test", "temporary-password", variables);

        when(objectMapper.readValue(message, EmailRequestDto.class)).thenReturn(dto);
        doThrow(new BusinessException(EmailErrorCode.SEND_FAILED))
                .when(emailService).sendEmail("user@example.com", "Test", "temporary-password", variables);

        assertDoesNotThrow(() -> emailKafkaConsumer.consume(message));
    }
}
