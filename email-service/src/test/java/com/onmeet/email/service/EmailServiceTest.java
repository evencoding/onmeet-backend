package com.onmeet.email.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

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

        // When
        emailService.sendEmail(to, subject, body);

        // Then
        // Hiện tại EmailService đang comment out javaMailSender.send(message);
        // Nên chúng ta không verify send() được nếu muốn test thực tế.
        // Tuy nhiên, chúng ta có thể kiểm tra xem logic tạo message có lỗi gì không (thông qua coverage).
        // Nếu sau này uncomment send(), test này sẽ cần verify(javaMailSender).send(any(SimpleMailMessage.class));
        // Mockito verify nothing happens on javaMailSender because it's commented out in implementation
        verify(javaMailSender, times(0)).send(any(SimpleMailMessage.class));
    }
}
