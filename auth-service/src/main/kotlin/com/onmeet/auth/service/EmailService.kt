package com.onmeet.auth.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.kafka.core.KafkaTemplate
import com.onmeet.auth.dto.EmailMessage

interface EmailService {
    fun sendInvitationEmail(to: String, code: String)
}

@Service
class EmailServiceImpl(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) : EmailService {
    companion object {
        private val log = LoggerFactory.getLogger(EmailServiceImpl::class.java)
        private const val TOPIC = "email-send-topic"
    }

    /**
     * [개발 팀 공유 사항]
     * 이메일 발송 요청을 Kafka로 전송하는 Producer 메소드입니다.
     *
     * 1. 실제 이메일을 발송하지 않고, 'email-service'가 수신할 수 있도록 메시지(이벤트)를 발행합니다.
     * 2. Kafka Topic: "email-send-topic"
     * 3. 전송 데이터: EmailMessage (수신자, 제목, 본문)
     *
     * 참고:
     * - 이 작업은 비동기로 처리됩니다.
     * - 실제 메일 발송은 'email-service'에서 수행됩니다.
     */
    override fun sendInvitationEmail(to: String, code: String) {
        EmailMessage(
            to = to,
            subject = "You are invited to join OnMeet",
            body = "Your invitation code is: $code. Please use this code to join your company on OnMeet."
        ).also { log.info("Sending invitation email event to Kafka topic: $TOPIC for $to") }
            .let { kafkaTemplate.send(TOPIC, it) }
    }
}
