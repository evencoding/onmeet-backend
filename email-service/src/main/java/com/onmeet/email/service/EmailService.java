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

    /*
    * [개발 팀 공유 사항]
    * 현재 AWS SES 또는 SMTP 설정이 완료되지 않은 상태입니다.
    * 이메일 발송 흐름을 테스트하기 위해 실제 메일 전송 대신 로그로 출력하도록 임시 처리되었습니다.
    *
    * [추후 작업 가이드]
    * 1. application.yml에서 spring.mail 관련 설정을 실제 환경에 맞게 구성하세요.
    * 2. 아래 주석 처리된 `javaMailSender.send(message);` 라인의 주석을 해제하세요.
    * 3. 로컬 테스트 중이라면 Docker Compose의 mailhog 등을 활용할 수도 있습니다.
    */
    public void sendEmail(String to, String subject, String body) {
        log.info("============== [EMAIL SEND SIMULATION] ==============");
        log.info("To: {}", to);
        log.info("Subject: {}", subject);
        log.info("Body: {}", body);
        log.info("=====================================================");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom(fromEmail);

            // TODO: 실제 SMTP 설정이 완료되면 아래 주석을 해제하여 메일을 발송하세요.
            // javaMailSender.send(message);
            // log.info("Email sent successfully to: {}", to);
        } catch (org.springframework.mail.MailException e) {
            log.error("Failed to make email object for: {}", to, e);
        }
    }
}
