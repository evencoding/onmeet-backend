package com.onmeet.auth.service

import com.onmeet.auth.dto.ChangePasswordRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.BusinessException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

@ExtendWith(MockKExtension::class)
class PasswordServiceTest {

    @MockK
    private lateinit var emailService: EmailService

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var notificationEventPublisher: NotificationEventPublisher

    @InjectMockKs
    private lateinit var passwordService: PasswordService

    private val company = Company(id = 1L, name = "Test Company")
    private val user = User(
        id = 1L,
        email = "test@test.com",
        passwordHash = "hashed_old",
        name = "Test User",
        roles = mutableSetOf(User.Role.USER),
        company = company,
        status = User.UserStatus.ACTIVE
    )

    @Test
    fun `changePassword should update password hash when old password matches`() {
        // given
        val request = ChangePasswordRequest(currentPassword = "old_pass", newPassword = "new_pass")
        every { userRepository.findByEmail("test@test.com") } returns Optional.of(user)
        every { passwordEncoder.matches("old_pass", "hashed_old") } returns true
        every { passwordEncoder.encode("new_pass") } returns "hashed_new"
        every { userRepository.save(any()) } returns user
        every { notificationEventPublisher.publishNotification(any()) } returns Unit

        // when
        passwordService.changePassword("test@test.com", request)

        // then
        assertEquals("hashed_new", user.passwordHash)
        assertFalse(user.isPasswordReset)
        verify { userRepository.save(user) }
    }

    @Test
    fun `changePassword should reset isPasswordReset flag to false`() {
        // given
        val userWithResetFlag = User(
            id = 1L, email = "test@test.com", passwordHash = "hashed_temp", name = "Test User",
            roles = mutableSetOf(User.Role.USER), company = company, status = User.UserStatus.ACTIVE
        ).apply { isPasswordReset = true }
        val request = ChangePasswordRequest(currentPassword = "temp_pass", newPassword = "new_pass")

        every { userRepository.findByEmail("test@test.com") } returns Optional.of(userWithResetFlag)
        every { passwordEncoder.matches("temp_pass", "hashed_temp") } returns true
        every { passwordEncoder.encode("new_pass") } returns "hashed_new"
        every { userRepository.save(any()) } returns userWithResetFlag
        every { notificationEventPublisher.publishNotification(any()) } returns Unit

        // when
        passwordService.changePassword("test@test.com", request)

        // then
        assertFalse(userWithResetFlag.isPasswordReset)
    }

    @Test
    fun `changePassword should throw BusinessException when old password does not match`() {
        // given
        val request = ChangePasswordRequest(currentPassword = "wrong_pass", newPassword = "new_pass")
        every { userRepository.findByEmail("test@test.com") } returns Optional.of(user)
        every { passwordEncoder.matches("wrong_pass", "hashed_old") } returns false

        // when & then
        assertThrows<BusinessException> {
            passwordService.changePassword("test@test.com", request)
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `changePassword should throw BusinessException when user not found`() {
        // given
        val request = ChangePasswordRequest(currentPassword = "old", newPassword = "new")
        every { userRepository.findByEmail("missing@test.com") } returns Optional.empty()

        // when & then
        assertThrows<BusinessException> {
            passwordService.changePassword("missing@test.com", request)
        }
    }

    @Test
    fun `findPassword should generate 8-character temporary password and send email`() {
        // given
        val tempPasswordSlot = slot<String>()
        every { userRepository.findByEmail("test@test.com") } returns Optional.of(user)
        every { passwordEncoder.encode(any()) } returns "hashed_temp"
        every { userRepository.save(any()) } returns user
        every { emailService.sendTemporaryPassword("test@test.com", capture(tempPasswordSlot), "Test User") } returns Unit

        // when
        passwordService.findPassword("test@test.com")

        // then
        assertTrue(user.isPasswordReset)
        assertEquals(8, tempPasswordSlot.captured.length)
        assertTrue(tempPasswordSlot.captured.matches(Regex("^[A-Za-z0-9!@#\$%^&*]+$")))
        verify { emailService.sendTemporaryPassword(eq("test@test.com"), any(), eq("Test User")) }
    }

    @Test
    fun `findPassword should set isPasswordReset flag to true`() {
        // given
        every { userRepository.findByEmail("test@test.com") } returns Optional.of(user)
        every { passwordEncoder.encode(any()) } returns "hashed_temp"
        every { userRepository.save(any()) } returns user
        every { emailService.sendTemporaryPassword(any(), any(), any()) } returns Unit

        // when
        passwordService.findPassword("test@test.com")

        // then
        assertTrue(user.isPasswordReset)
        verify { userRepository.save(match { it.isPasswordReset }) }
    }

    @Test
    fun `findPassword should throw BusinessException when user not found`() {
        // given
        every { userRepository.findByEmail("missing@test.com") } returns Optional.empty()

        // when & then
        assertThrows<BusinessException> {
            passwordService.findPassword("missing@test.com")
        }
        verify(exactly = 0) { emailService.sendTemporaryPassword(any(), any(), any()) }
    }
}
