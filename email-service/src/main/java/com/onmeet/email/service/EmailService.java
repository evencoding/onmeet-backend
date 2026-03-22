package com.onmeet.email.service;

import java.util.Map;
import java.util.Set;
import jakarta.mail.internet.MimeMessage;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.EmailErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // [Security] Path Traversal 방지: Kafka 메시지에서 수신한 templateName을 화이트리스트로 검증
    private static final Set<String> ALLOWED_TEMPLATES = Set.of(
            "company-invitation",
            "guest-invitation",
            "temporary-password"
    );

    private final JavaMailSender javaMailSender;
    private final GmailOAuth2TokenService tokenService;
    private final TemplateEngine templateEngine;

    @org.springframework.beans.factory.annotation.Value("${email.from}")
    private String fromEmail;

    public EmailService(JavaMailSender javaMailSender, GmailOAuth2TokenService tokenService, TemplateEngine templateEngine) {
        this.javaMailSender = javaMailSender;
        this.tokenService = tokenService;
        this.templateEngine = templateEngine;
    }

    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        // [Security] templateName 화이트리스트 검증 (Path Traversal 방지)
        if (!ALLOWED_TEMPLATES.contains(templateName)) {
            log.error("Rejected illegal templateName '{}'. Allowed templates: {}", templateName, ALLOWED_TEMPLATES);
            throw new BusinessException(EmailErrorCode.INVALID_TEMPLATE);
        }

        try {
            // OAuth2 Access Token 설정
            configureOAuth2TokenIfPossible();

            // Thymeleaf Template Process
            Context context = new Context();
            if (variables != null) {
                context.setVariables(variables);
            }
            String htmlBody = templateEngine.process(templateName, context);

            // 메일 구성 (MimeMessage for HTML)
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom(fromEmail);

            // 메일 발송
            javaMailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            throw new BusinessException(EmailErrorCode.SEND_FAILED);
        }
    }

    private void configureOAuth2TokenIfPossible() {
        if (javaMailSender instanceof JavaMailSenderImpl implementation) {
            try {
                String accessToken = tokenService.getAccessToken();
                implementation.setPassword(accessToken);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(EmailErrorCode.OAUTH2_TOKEN_CONFIG_FAILED);
            }
        }
    }
}
