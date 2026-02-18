package com.onmeet.auth.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

interface EmailService {
    fun sendInvitationEmail(to: String, code: String)
}

@Service
class EmailServiceImpl : EmailService {
    companion object {
        private val log = LoggerFactory.getLogger(EmailServiceImpl::class.java)
    }

    override fun sendInvitationEmail(to: String, code: String) {
        log.info("Sending invitation email to: $to with code: $code")
        // Actual email sending logic (SMTP, AWS SES, etc.) will go here
    }
}
