package com.onmeet.email.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender javaMailSender;
    private final GmailOAuth2TokenService tokenService;

    @org.springframework.beans.factory.annotation.Value("${email.from}")
    private String fromEmail;

    public EmailService(JavaMailSender javaMailSender, GmailOAuth2TokenService tokenService) {
        this.javaMailSender = javaMailSender;
        this.tokenService = tokenService;
    }

    public void sendEmail(String to, String subject, String body) {
        log.info("============== [EMAIL SENDING] ==============");
        log.info("To: {}", to);
        log.info("Subject: {}", subject);
        log.info("=============================================");

        try {
            // Get fresh Access Token from Google
            String accessToken = tokenService.getAccessToken();

            // Set the token as password dynamically for XOAUTH2
            if (javaMailSender instanceof JavaMailSenderImpl mailSenderImpl) {
                mailSenderImpl.setPassword(accessToken);
            } else {
                log.warn("JavaMailSender is not an instance of JavaMailSenderImpl. Cannot inject access token.");
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom(fromEmail);

            javaMailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
        }
    }
}
