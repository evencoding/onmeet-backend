package com.onmeet.auth.service

import com.onmeet.auth.dto.ChangePasswordRequest
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.dto.NotificationRequestDto
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PasswordService(
    private val emailService: EmailService,
    private val passwordEncoder: PasswordEncoder,
    private val userRepository: UserRepository,
    private val notificationEventPublisher: NotificationEventPublisher
) {
    private val log = LoggerFactory.getLogger(PasswordService::class.java)

    companion object {
        private const val TEMP_PASSWORD_LENGTH = 8
        private const val TEMP_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
    }

    @Transactional
    fun changePassword(email: String, request: ChangePasswordRequest) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

        if (!passwordEncoder.matches(request.oldPassword, user.passwordHash)) {
            throw BusinessException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH)
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
        user.isPasswordReset = false
        val updatedUser = userRepository.save(user)
        log.info("Password changed successfully for user: $email, isPasswordReset flag reset to false")

        notificationEventPublisher.publishNotification(
            NotificationRequestDto(
                userId = updatedUser.id,
                type = "SYSTEM",
                title = "비밀번호 변경 완료",
                body = "비밀번호가 성공적으로 변경되었습니다. 본인이 아닐 경우 관리자에게 문의하세요."
            )
        )
    }

    @Transactional
    fun findPassword(email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { BusinessException(AuthErrorCode.USER_EMAIL_NOT_FOUND) }

        val temporaryPassword = generateTemporaryPassword()
        user.passwordHash = passwordEncoder.encode(temporaryPassword)
        user.isPasswordReset = true
        userRepository.save(user)

        emailService.sendTemporaryPassword(email, temporaryPassword, user.name)
        log.info("Temporary password generated and sent for user: $email")
    }

    private fun generateTemporaryPassword(): String {
        val random = java.security.SecureRandom()
        return (1..TEMP_PASSWORD_LENGTH)
            .map { TEMP_PASSWORD_CHARS[random.nextInt(TEMP_PASSWORD_CHARS.length)] }
            .joinToString("")
    }
}
