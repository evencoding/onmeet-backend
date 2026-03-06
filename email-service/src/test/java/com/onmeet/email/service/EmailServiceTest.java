package com.onmeet.email.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private GmailOAuth2TokenService tokenService;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@onmeet.com");
    }

    @Test
    void sendEmail_ShouldLogAndConstructMessage() {
        // Given
        String to = "user@example.com";
        String subject = "Test Subject";
        String body = "Test Body";
        String fakeToken = "ya29.fakeToken";

        when(tokenService.getAccessToken()).thenReturn(fakeToken);

        // When
        emailService.sendEmail(to, subject, body);

        // Then
        // Verify that token fetching and mail sending was called
        verify(tokenService, times(1)).getAccessToken();
        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
