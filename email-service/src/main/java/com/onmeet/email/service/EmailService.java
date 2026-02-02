package com.onmeet.email.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender javaMailSender;

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @org.springframework.beans.factory.annotation.Value("${email.from}")
    private String fromEmail;

    public void sendEmail(String to, String subject, String body) {
        log.info("Sending email to: {}", to);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom(fromEmail);

            javaMailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (org.springframework.mail.MailException e) {
            log.error("Failed to send email to: {}", to, e);
        }
    }
}
