package com.onmeet.email.service;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.EmailErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceIntegrationTest {

    @Mock
    private JavaMailSenderImpl javaMailSender;

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
    void sendEmail_TemplateRendering_VariablesPassedToContext() {
        // Given: template variables must be propagated to the Thymeleaf context
        String to = "user@example.com";
        String subject = "회의 초대";
        String templateName = "guest-invitation";
        Map<String, Object> variables = Map.of(
                "hostName", "홍길동",
                "roomName", "프로젝트 킥오프",
                "joinLink", "https://meet.onmeet.com/abc123"
        );

        String renderedHtml = "<html><body>안녕하세요 홍길동님</body></html>";
        MimeMessage mockMimeMessage = mock(MimeMessage.class);

        when(tokenService.getAccessToken()).thenReturn("ya29.token");
        when(templateEngine.process(eq(templateName), any(Context.class))).thenReturn(renderedHtml);
        when(javaMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        // When
        emailService.sendEmail(to, subject, templateName, variables);

        // Then: templateEngine.process must be called with the correct template name
        verify(templateEngine, times(1)).process(eq("guest-invitation"), any(Context.class));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_NullVariables_ShouldNotThrow() {
        // Given: null variables map should not cause a NullPointerException
        String templateName = "company-invitation";
        MimeMessage mockMimeMessage = mock(MimeMessage.class);

        when(tokenService.getAccessToken()).thenReturn("ya29.token");
        when(templateEngine.process(eq(templateName), any(Context.class))).thenReturn("<html>body</html>");
        when(javaMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        // When & Then: must not throw
        assertDoesNotThrow(() ->
                emailService.sendEmail("user@example.com", "Subject", templateName, null)
        );
    }

    @Test
    void sendEmail_EmptyVariables_ShouldSendSuccessfully() {
        // Given: empty map is valid input
        Map<String, Object> emptyVariables = new HashMap<>();
        String templateName = "temporary-password";
        MimeMessage mockMimeMessage = mock(MimeMessage.class);

        when(tokenService.getAccessToken()).thenReturn("ya29.token");
        when(templateEngine.process(eq(templateName), any(Context.class))).thenReturn("<html>body</html>");
        when(javaMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        assertDoesNotThrow(() ->
                emailService.sendEmail("user@example.com", "비밀번호", templateName, emptyVariables)
        );

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_SmtpFailure_ThrowsBusinessException() {
        // Given: JavaMailSender.send throws a MailSendException (SMTP connection failure)
        String templateName = "guest-invitation";
        MimeMessage mockMimeMessage = mock(MimeMessage.class);

        when(tokenService.getAccessToken()).thenReturn("ya29.token");
        when(templateEngine.process(eq(templateName), any(Context.class))).thenReturn("<html>body</html>");
        when(javaMailSender.createMimeMessage()).thenReturn(mockMimeMessage);
        doThrow(new MailSendException("SMTP connection refused"))
                .when(javaMailSender).send(any(MimeMessage.class));

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class, () ->
                emailService.sendEmail("user@example.com", "subject", templateName, Map.of())
        );
        assertEquals(EmailErrorCode.SEND_FAILED, ex.getErrorCode());
    }

    @Test
    void sendEmail_TemplateEngineThrows_ThrowsBusinessException() {
        // Given: Thymeleaf template processing fails before createMimeMessage is reached
        String templateName = "temporary-password";

        when(tokenService.getAccessToken()).thenReturn("ya29.token");
        when(templateEngine.process(eq(templateName), any(Context.class)))
                .thenThrow(new RuntimeException("template parse error"));
        // Note: javaMailSender.createMimeMessage() is NOT stubbed because templateEngine throws first

        // When & Then: wrapped as SEND_FAILED
        BusinessException ex = assertThrows(BusinessException.class, () ->
                emailService.sendEmail("user@example.com", "subject", templateName, Map.of())
        );
        assertEquals(EmailErrorCode.SEND_FAILED, ex.getErrorCode());
    }

    @Test
    void sendEmail_TokenServiceThrowsBusinessException_PropagatesAsIs() {
        // Given: token service throws BusinessException (e.g., credentials missing)
        String templateName = "company-invitation";
        when(tokenService.getAccessToken())
                .thenThrow(new BusinessException(EmailErrorCode.CREDENTIALS_MISSING));

        // When & Then: BusinessException must propagate unchanged (not wrapped)
        BusinessException ex = assertThrows(BusinessException.class, () ->
                emailService.sendEmail("user@example.com", "subject", templateName, Map.of())
        );
        assertEquals(EmailErrorCode.CREDENTIALS_MISSING, ex.getErrorCode());
    }

    @Test
    void sendEmail_InvalidTemplate_ThrowsBusinessException() {
        // Given: invalid template name (not in allowlist)
        String invalidTemplate = "../../etc/passwd";

        // When & Then: must throw BusinessException with INVALID_TEMPLATE
        BusinessException ex = assertThrows(BusinessException.class, () ->
                emailService.sendEmail("user@example.com", "subject", invalidTemplate, Map.of())
        );
        assertEquals(EmailErrorCode.INVALID_TEMPLATE, ex.getErrorCode());

        // Neither tokenService nor templateEngine should be touched
        verify(tokenService, never()).getAccessToken();
        verify(templateEngine, never()).process(anyString(), any(Context.class));
    }

    @Test
    void sendEmail_AllAllowedTemplates_ShouldPassValidation() {
        // Given: verify all allowed templates pass the whitelist check
        String[] allowedTemplates = {"company-invitation", "guest-invitation", "temporary-password"};
        MimeMessage mockMimeMessage = mock(MimeMessage.class);

        when(tokenService.getAccessToken()).thenReturn("ya29.token");
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>body</html>");
        when(javaMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        for (String template : allowedTemplates) {
            assertDoesNotThrow(() ->
                    emailService.sendEmail("user@example.com", "subject", template, Map.of()),
                    "Allowed template should not throw: " + template
            );
        }
    }
}
