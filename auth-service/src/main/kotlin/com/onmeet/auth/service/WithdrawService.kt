package com.onmeet.auth.service

import com.onmeet.auth.client.FileClient
import com.onmeet.auth.dto.WithdrawRequest
import com.onmeet.auth.entity.User
import com.onmeet.auth.entity.WithdrawnUser
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.auth.repository.jpa.WithdrawnUserRepository
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class WithdrawService(
    private val withdrawnUserRepository: WithdrawnUserRepository,
    private val fileClient: FileClient,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService
) {
    fun withdraw(email: String, request: WithdrawRequest) {
        val profileImageId = withdrawInternal(email, request)
        fileClient.safeDeleteProfileImageIfPresent(profileImageId, "withdraw email=$email")
        tokenService.revokeTokens(null, email)
    }

    @Transactional
    fun withdrawInternal(email: String, request: WithdrawRequest): Long? {
        val user = userRepository.findByEmail(email)
            .orElseThrow { BusinessException(AuthErrorCode.USER_NOT_FOUND) }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BusinessException(AuthErrorCode.INVALID_PASSWORD)
        }

        withdrawnUserRepository.save(
            WithdrawnUser(
                originalUserId = user.requireId(),
                email = user.email,
                name = user.name,
                reason = request.reason,
                withdrawnAt = LocalDateTime.now()
            )
        )

        val profileImageId = user.profileImageId
        user.email = "withdrawn_${user.id}@onmeet.deleted"
        user.name = "Withdrawn User"
        user.passwordHash = ""
        user.status = User.UserStatus.INACTIVE
        user.profileImageId = null
        userRepository.save(user)

        return profileImageId
    }
}
