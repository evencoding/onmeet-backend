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

    override fun sendInvitationEmail(to: String, code: String) {
        val subject = "You are invited to join OnMeet"
        val body = "Your invitation code is: $code. Please use this code to join your company on OnMeet."
        val emailMessage = EmailMessage(to, subject, body)
        
        log.info("Sending invitation email event to Kafka topic: $TOPIC for $to")
        kafkaTemplate.send(TOPIC, emailMessage)
    }
}
