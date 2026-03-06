package com.onmeet.email.service;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private GmailOAuth2TokenService tokenService;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@onmeet.com");
    }

    @Test
    void sendEmail_WithValidTemplate_ShouldSendMimeMessage() throws Exception {
        // Given
        String to = "user@example.com";
        String subject = "Test Subject";
        String templateName = "guest-invitation";
        Map<String, Object> variables = Map.of("hostName", "Host", "roomName", "Room", "joinLink", "http://link");
        String fakeToken = "ya29.fakeToken";
        String fakeHtmlBody = "<html><body>Test</body></html>";

        MimeMessage mockMimeMessage = org.mockito.Mockito.mock(MimeMessage.class);
        when(tokenService.getAccessToken()).thenReturn(fakeToken);
        when(templateEngine.process(eq(templateName), any(Context.class))).thenReturn(fakeHtmlBody);
        when(javaMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        // When
        emailService.sendEmail(to, subject, templateName, variables);

        // Then: OAuth2 토큰 발급 및 MimeMessage 전송 검증
        verify(tokenService, times(1)).getAccessToken();
        verify(templateEngine, times(1)).process(eq(templateName), any(Context.class));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_WithInvalidTemplate_ShouldThrowException() {
        // Given
        String to = "user@example.com";
        String subject = "Test Subject";
        String maliciousTemplateName = "../../../etc/passwd";

        // When & Then: 허용 목록에 없는 template은 IllegalArgumentException을 발생시켜야 함
        assertThrows(IllegalArgumentException.class, ()
                -> emailService.sendEmail(to, subject, maliciousTemplateName, null)
        );

        // 악의적인 templateName으로 인해 tokenService나 javaMailSender가 호출되면 안 됨
        verify(tokenService, never()).getAccessToken();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }
}
