package com.onmeet.auth.service

import com.onmeet.auth.dto.UserProfileUpdateRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.JobTitle
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.JobTitleRepository
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.mockk
import io.mockk.justRun
import io.mockk.slot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import org.springframework.web.multipart.MultipartFile
import com.onmeet.auth.client.FileMetadataResponse
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

@ExtendWith(MockKExtension::class)
class UserServiceImplTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var jobTitleRepository: JobTitleRepository

    @MockK
    private lateinit var fileClient: com.onmeet.auth.client.FileClient

    @MockK
    private lateinit var notificationEventPublisher: NotificationEventPublisher

    @InjectMockKs
    private lateinit var userService: UserServiceImpl

    // ===== updateUserProfile =====

    @Test
    fun `updateUserProfile should update profile when requester is self`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "Old", company = company)
        val request = UserProfileUpdateRequest(name = "New", employeeId = "EMP1", jobTitleId = null)

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userRepository.save(match { it.name == "New" && it.employeeId == "EMP1" }) } returns user

        // when
        val result = userService.updateUserProfile(1L, user, request)

        // then
        assertEquals("New", user.name)
        assertEquals("EMP1", user.employeeId)
        verify { userRepository.save(user) }
    }

    @Test
    fun `updateUserProfile should update profile when requester is manager in same company`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val manager = User(id = 2L, email = "mgr@test.com", passwordHash = "hash", name = "Mgr",
                          roles = mutableSetOf(User.Role.MANAGER), company = company)
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "Old", company = company)
        val request = UserProfileUpdateRequest(name = "New", employeeId = null, jobTitleId = null)

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userRepository.save(match { it.name == "New" }) } returns user

        // when
        userService.updateUserProfile(1L, manager, request)

        // then
        assertEquals("New", user.name)
        verify { userRepository.save(user) }
    }

    @Test
    fun `updateUserProfile should throw BusinessException with CROSS_COMPANY_ACCESS when manager updates user from another company`() {
        // given
        val company1 = Company(id = 1L, name = "Co1")
        val company2 = Company(id = 2L, name = "Co2")
        val manager = User(id = 10L, email = "mgr@test.com", passwordHash = "hash", name = "Mgr",
                          roles = mutableSetOf(User.Role.MANAGER), company = company1)
        val userFromOtherCo = User(id = 20L, email = "other@test.com", passwordHash = "hash", name = "Other", company = company2)
        val request = UserProfileUpdateRequest(name = "New", employeeId = null, jobTitleId = null)

        every { userRepository.findById(20L) } returns Optional.of(userFromOtherCo)

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.updateUserProfile(20L, manager, request)
        }
        assertEquals(AuthErrorCode.CROSS_COMPANY_ACCESS, exception.errorCode)
    }

    @Test
    fun `updateUserProfile should throw BusinessException with PROFILE_UPDATE_FORBIDDEN when non-self non-manager updates`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val requester = User(id = 2L, email = "other@test.com", passwordHash = "hash", name = "Other",
                            roles = mutableSetOf(User.Role.USER), company = company)
        val targetUser = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "Target", company = company)
        val request = UserProfileUpdateRequest(name = "New", employeeId = null, jobTitleId = null)

        every { userRepository.findById(1L) } returns Optional.of(targetUser)

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.updateUserProfile(1L, requester, request)
        }
        assertEquals(AuthErrorCode.PROFILE_UPDATE_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun `updateUserProfile should throw BusinessException with USER_NOT_FOUND when user does not exist`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val requester = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User", company = company)
        val request = UserProfileUpdateRequest(name = "New", employeeId = null, jobTitleId = null)

        every { userRepository.findById(99L) } returns Optional.empty()

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.updateUserProfile(99L, requester, request)
        }
        assertEquals(AuthErrorCode.USER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `updateUserProfile should update jobTitle when jobTitleId is provided`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User", company = company)
        val jobTitle = JobTitle(id = 10L, name = "Senior", company = company)
        val request = UserProfileUpdateRequest(name = null, employeeId = null, jobTitleId = 10L)

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { jobTitleRepository.findById(10L) } returns Optional.of(jobTitle)
        every { userRepository.save(match { it.jobTitle == jobTitle }) } returns user

        // when
        userService.updateUserProfile(1L, user, request)

        // then
        assertEquals(jobTitle, user.jobTitle)
        verify { userRepository.save(user) }
    }

    @Test
    fun `updateUserProfile should throw BusinessException with JOB_TITLE_NOT_FOUND when jobTitle does not exist`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User", company = company)
        val request = UserProfileUpdateRequest(name = null, employeeId = null, jobTitleId = 999L)

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { jobTitleRepository.findById(999L) } returns Optional.empty()

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.updateUserProfile(1L, user, request)
        }
        assertEquals(AuthErrorCode.JOB_TITLE_NOT_FOUND, exception.errorCode)
    }

    // ===== deactivateUser =====

    @Test
    fun `deactivateUser should change user status to INACTIVE and publish notification`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val manager = User(id = 1L, email = "mgr@test.com", passwordHash = "hash", name = "Mgr",
                          roles = mutableSetOf(User.Role.MANAGER), company = company)
        val target = User(id = 2L, email = "target@test.com", passwordHash = "hash", name = "Target", company = company)

        every { userRepository.findById(2L) } returns Optional.of(target)
        every { userRepository.save(match { it.status == User.UserStatus.INACTIVE }) } returns target
        justRun { notificationEventPublisher.publishNotification(any()) }

        // when
        userService.deactivateUser(2L, manager)

        // then
        assertEquals(User.UserStatus.INACTIVE, target.status)
        verify { userRepository.save(target) }
        verify { notificationEventPublisher.publishNotification(match {
            it.userId == target.id && it.type == "SYSTEM" && it.title == "계정 비활성화"
        }) }
    }

    @Test
    fun `deactivateUser should throw BusinessException with USER_STATUS_CHANGE_FORBIDDEN when requester is not manager`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User",
                       roles = mutableSetOf(User.Role.USER), company = company)
        val target = User(id = 2L, email = "target@test.com", passwordHash = "hash", name = "Target", company = company)

        every { userRepository.findById(2L) } returns Optional.of(target)

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.deactivateUser(2L, user)
        }
        assertEquals(AuthErrorCode.USER_STATUS_CHANGE_FORBIDDEN, exception.errorCode)
    }

    @Test
    fun `deactivateUser should throw BusinessException with CROSS_COMPANY_MANAGE_FORBIDDEN when manager is from different company`() {
        // given
        val company1 = Company(id = 1L, name = "Co1")
        val company2 = Company(id = 2L, name = "Co2")
        val manager = User(id = 1L, email = "mgr@test.com", passwordHash = "hash", name = "Mgr",
                          roles = mutableSetOf(User.Role.MANAGER), company = company1)
        val target = User(id = 2L, email = "target@test.com", passwordHash = "hash", name = "Target", company = company2)

        every { userRepository.findById(2L) } returns Optional.of(target)

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.deactivateUser(2L, manager)
        }
        assertEquals(AuthErrorCode.CROSS_COMPANY_MANAGE_FORBIDDEN, exception.errorCode)
    }

    // ===== activateUser =====

    @Test
    fun `activateUser should change user status to ACTIVE and publish notification`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val manager = User(id = 1L, email = "mgr@test.com", passwordHash = "hash", name = "Mgr",
                          roles = mutableSetOf(User.Role.MANAGER), company = company)
        val target = User(id = 2L, email = "target@test.com", passwordHash = "hash", name = "Target",
                         company = company, status = User.UserStatus.INACTIVE)

        every { userRepository.findById(2L) } returns Optional.of(target)
        every { userRepository.save(match { it.status == User.UserStatus.ACTIVE }) } returns target
        justRun { notificationEventPublisher.publishNotification(any()) }

        // when
        userService.activateUser(2L, manager)

        // then
        assertEquals(User.UserStatus.ACTIVE, target.status)
        verify { userRepository.save(target) }
        verify { notificationEventPublisher.publishNotification(match {
            it.userId == target.id && it.type == "SYSTEM" && it.title == "계정 활성화"
        }) }
    }

    @Test
    fun `activateUser should throw BusinessException with USER_STATUS_CHANGE_FORBIDDEN when requester is not manager`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User",
                       roles = mutableSetOf(User.Role.USER), company = company)

        every { userRepository.findById(2L) } returns Optional.of(
            User(id = 2L, email = "target@test.com", passwordHash = "hash", name = "Target", company = company)
        )

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.activateUser(2L, user)
        }
        assertEquals(AuthErrorCode.USER_STATUS_CHANGE_FORBIDDEN, exception.errorCode)
    }

    // ===== getUserInfo =====

    @Test
    fun `getUserInfo should return user info when requester is in same company`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val requester = User(id = 1L, email = "requester@test.com", passwordHash = "hash", name = "Requester", company = company)
        val target = User(id = 2L, email = "target@test.com", passwordHash = "hash", name = "Target", company = company)

        every { userRepository.findById(2L) } returns Optional.of(target)

        // when
        val result = userService.getUserInfo(2L, requester)

        // then
        assertEquals(target.id, result.id)
        assertEquals(target.name, result.name)
    }

    @Test
    fun `getUserInfo should throw BusinessException with CROSS_COMPANY_ACCESS when requester is from different company`() {
        // given
        val company1 = Company(id = 1L, name = "Co1")
        val company2 = Company(id = 2L, name = "Co2")
        val requester = User(id = 1L, email = "requester@test.com", passwordHash = "hash", name = "Requester", company = company1)
        val target = User(id = 2L, email = "target@test.com", passwordHash = "hash", name = "Target", company = company2)

        every { userRepository.findById(2L) } returns Optional.of(target)

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.getUserInfo(2L, requester)
        }
        assertEquals(AuthErrorCode.CROSS_COMPANY_ACCESS, exception.errorCode)
    }

    @Test
    fun `getUserInfo should throw BusinessException with USER_NOT_FOUND when user does not exist`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val requester = User(id = 1L, email = "requester@test.com", passwordHash = "hash", name = "Requester", company = company)

        every { userRepository.findById(99L) } returns Optional.empty()

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.getUserInfo(99L, requester)
        }
        assertEquals(AuthErrorCode.USER_NOT_FOUND, exception.errorCode)
    }

    // ===== getAllEmployees =====

    @Test
    fun `getAllEmployees should return page of employees when requester is manager`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val manager = User(id = 1L, email = "mgr@test.com", passwordHash = "hash", name = "Mgr",
                          roles = mutableSetOf(User.Role.MANAGER), company = company)
        val employee = User(id = 2L, email = "emp@test.com", passwordHash = "hash", name = "Emp", company = company)
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl(listOf(employee), pageable, 1)

        every { userRepository.findByCompany(company, pageable) } returns page

        // when
        val result = userService.getAllEmployees(manager, pageable)

        // then
        assertEquals(1, result.content.size)
        assertEquals(employee.id, result.content[0].id)
    }

    @Test
    fun `getAllEmployees should throw BusinessException with EMPLOYEE_LIST_FORBIDDEN when requester is not manager`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User",
                       roles = mutableSetOf(User.Role.USER), company = company)
        val pageable = PageRequest.of(0, 10)

        // when & then
        val exception = assertThrows<BusinessException> {
            userService.getAllEmployees(user, pageable)
        }
        assertEquals(AuthErrorCode.EMPLOYEE_LIST_FORBIDDEN, exception.errorCode)
    }

    // ===== deleteMyProfileImage =====

    @Test
    fun `deleteMyProfileImage should call fileClient and clear profileImageId`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User",
                       profileImageId = 100L, company = company)

        every { fileClient.deleteMyProfileImage() } returns Unit
        every { userRepository.save(match { it.profileImageId == null }) } returns user

        // when
        val result = userService.deleteMyProfileImage(user)

        // then
        assertNull(user.profileImageId)
        verify { fileClient.deleteMyProfileImage() }
        verify { userRepository.save(user) }
    }

    // ===== getMyInfo =====

    @Test
    fun `getMyInfo should return user response dto`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "Test User", company = company)

        // when
        val result = userService.getMyInfo(user)

        // then
        assertEquals(user.id, result.id)
        assertEquals(user.name, result.name)
        assertEquals(user.email, result.email)
    }

    // ===== updateUserProfile with profile image =====

    @Test
    fun `updateUserProfile should upload new profile image when profileImage is provided`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User", company = company)
        val request = UserProfileUpdateRequest(name = "Updated", employeeId = null, jobTitleId = null)
        val mockFile = mockk<MultipartFile>()
        val uploadedFile = FileMetadataResponse(id = 200L, fileName = "profile.jpg", s3Url = "https://s3.example.com/profile.jpg", contentType = "image/jpeg")

        every { mockFile.isEmpty } returns false
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { fileClient.uploadProfileImage(mockFile, "1") } returns uploadedFile
        every { userRepository.save(match { it.profileImageId == 200L && it.name == "Updated" }) } returns user

        // when
        val result = userService.updateUserProfile(1L, user, request, mockFile)

        // then
        assertEquals(200L, user.profileImageId)
        assertEquals("Updated", user.name)
        verify { fileClient.uploadProfileImage(mockFile, "1") }
        verify { userRepository.save(user) }
    }

    @Test
    fun `updateUserProfile should delete old profile image before uploading new one`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User",
                       profileImageId = 100L, company = company)
        val request = UserProfileUpdateRequest(name = null, employeeId = null, jobTitleId = null)
        val mockFile = mockk<MultipartFile>()
        val uploadedFile = FileMetadataResponse(id = 200L, fileName = "new-profile.jpg", s3Url = "https://s3.example.com/new-profile.jpg", contentType = "image/jpeg")

        every { mockFile.isEmpty } returns false
        every { userRepository.findById(1L) } returns Optional.of(user)
        justRun { fileClient.deleteMyProfileImage() }
        every { fileClient.uploadProfileImage(mockFile, "1") } returns uploadedFile
        every { userRepository.save(match { it.profileImageId == 200L }) } returns user

        // when
        userService.updateUserProfile(1L, user, request, mockFile)

        // then
        assertEquals(200L, user.profileImageId)
        verify { fileClient.deleteMyProfileImage() }
        verify { fileClient.uploadProfileImage(mockFile, "1") }
        verify { userRepository.save(user) }
    }

    @Test
    fun `updateUserProfile should not upload image when profileImage is null`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User", company = company)
        val request = UserProfileUpdateRequest(name = "Updated", employeeId = null, jobTitleId = null)

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userRepository.save(match { it.name == "Updated" }) } returns user

        // when
        userService.updateUserProfile(1L, user, request, null)

        // then
        assertEquals("Updated", user.name)
        verify(exactly = 0) { fileClient.uploadProfileImage(any(), any()) }
        verify { userRepository.save(user) }
    }

    @Test
    fun `updateUserProfile should not upload image when profileImage is empty`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User", company = company)
        val request = UserProfileUpdateRequest(name = "Updated", employeeId = null, jobTitleId = null)
        val mockFile = mockk<MultipartFile>()

        every { mockFile.isEmpty } returns true
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userRepository.save(match { it.name == "Updated" }) } returns user

        // when
        userService.updateUserProfile(1L, user, request, mockFile)

        // then
        assertEquals("Updated", user.name)
        verify(exactly = 0) { fileClient.uploadProfileImage(any(), any()) }
        verify { userRepository.save(user) }
    }

    // ===== getBatchFcmTokens =====

    @Test
    fun `getBatchFcmTokens should return tokens for users that have fcmDeviceToken`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user1 = User(id = 1L, email = "user1@test.com", passwordHash = "hash", name = "User1",
                        company = company, fcmDeviceToken = "token-aaa")
        val user2 = User(id = 2L, email = "user2@test.com", passwordHash = "hash", name = "User2",
                        company = company, fcmDeviceToken = "token-bbb")
        val userIds = listOf(1L, 2L)

        every { userRepository.findAllById(userIds) } returns listOf(user1, user2)

        // when
        val result = userService.getBatchFcmTokens(userIds)

        // then
        assertEquals(2, result.size)
        assertEquals(listOf("token-aaa"), result[1L])
        assertEquals(listOf("token-bbb"), result[2L])
    }

    @Test
    fun `getBatchFcmTokens should exclude users without fcmDeviceToken`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user1 = User(id = 1L, email = "user1@test.com", passwordHash = "hash", name = "User1",
                        company = company, fcmDeviceToken = "token-aaa")
        val user2 = User(id = 2L, email = "user2@test.com", passwordHash = "hash", name = "User2",
                        company = company, fcmDeviceToken = null)
        val userIds = listOf(1L, 2L)

        every { userRepository.findAllById(userIds) } returns listOf(user1, user2)

        // when
        val result = userService.getBatchFcmTokens(userIds)

        // then
        assertEquals(1, result.size)
        assertEquals(listOf("token-aaa"), result[1L])
        assertFalse(result.containsKey(2L))
    }

    @Test
    fun `getBatchFcmTokens should return empty map when no users have fcmDeviceToken`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user1 = User(id = 1L, email = "user1@test.com", passwordHash = "hash", name = "User1",
                        company = company, fcmDeviceToken = null)
        val userIds = listOf(1L)

        every { userRepository.findAllById(userIds) } returns listOf(user1)

        // when
        val result = userService.getBatchFcmTokens(userIds)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getBatchFcmTokens should return empty map when userIds is empty`() {
        // given
        val userIds = emptyList<Long>()
        every { userRepository.findAllById(userIds) } returns emptyList()

        // when
        val result = userService.getBatchFcmTokens(userIds)

        // then
        assertTrue(result.isEmpty())
    }
}
