package com.onmeet.auth.service

import com.onmeet.auth.dto.UserProfileUpdateRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.JobTitle
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.CompanyMismatchException
import com.onmeet.auth.exception.UserNotFoundException
import com.onmeet.auth.repository.jpa.JobTitleRepository
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.InsufficientPermissionException
import com.onmeet.common.exception.CrossCompanyAccessException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.mockk
import io.mockk.justRun
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.multipart.MultipartFile
import com.onmeet.auth.client.FileMetadataResponse

@ExtendWith(MockKExtension::class)
class UserServiceImplTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var jobTitleRepository: JobTitleRepository

    @MockK
    private lateinit var fileClient: com.onmeet.auth.client.FileClient

    @InjectMockKs
    private lateinit var userService: UserServiceImpl

    @Test
    // [Essential] 본인 프로필 수정 권한 및 필드 업데이트 검증
    fun `updateUserProfile should update profile when requester is self`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "Old", company = company)
        val request = UserProfileUpdateRequest(name = "New", employeeId = "EMP1", jobTitleId = null)

        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userRepository.save(any()) } returns user

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
        every { userRepository.save(any()) } returns user

        // when
        userService.updateUserProfile(1L, manager, request)

        // then
        assertEquals("New", user.name)
        verify { userRepository.save(user) }
    }

    @Test
    fun `updateUserProfile should throw CrossCompanyAccessException when manager updates user from another company`() {
        // given
        val company1 = Company(id = 1L, name = "Co1")
        val company2 = Company(id = 2L, name = "Co2")
        val manager = User(id = 10L, email = "mgr@test.com", passwordHash = "hash", name = "Mgr", 
                          roles = mutableSetOf(User.Role.MANAGER), company = company1)
        val userFromOtherCo = User(id = 20L, email = "other@test.com", passwordHash = "hash", name = "Other", company = company2)
        val request = UserProfileUpdateRequest(name = "New", employeeId = null, jobTitleId = null)

        every { userRepository.findById(20L) } returns Optional.of(userFromOtherCo)

        assertThrows(CrossCompanyAccessException::class.java) {
            userService.updateUserProfile(20L, manager, request)
        }
    }

    @Test
    // [Essential] 사용자 비활성화 로직 및 관리자 권한 검증
    fun `deactivateUser should change user status to INACTIVE`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val manager = User(id = 1L, email = "mgr@test.com", passwordHash = "hash", name = "Mgr", 
                          roles = mutableSetOf(User.Role.MANAGER), company = company)
        val target = User(id = 2L, email = "target@test.com", passwordHash = "hash", name = "Target", company = company)

        every { userRepository.findById(2L) } returns Optional.of(target)
        every { userRepository.save(any()) } returns target

        // when
        userService.deactivateUser(2L, manager)

        // then
        assertEquals(User.UserStatus.INACTIVE, target.status)
        verify { userRepository.save(target) }
    }

    @Test
    fun `deactivateUser should throw InsufficientPermissionException when requester is not manager`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User", 
                       roles = mutableSetOf(User.Role.USER), company = company)
        val target = User(id = 2L, email = "target@test.com", passwordHash = "hash", name = "Target", company = company)

        every { userRepository.findById(2L) } returns Optional.of(target)

        // when & then
        assertThrows(InsufficientPermissionException::class.java) {
            userService.deactivateUser(2L, user)
        }
    }

    @Test
    fun `deleteMyProfileImage should call fileClient and clear profileImageId`() {
        // given
        val company = Company(id = 1L, name = "TestCo")
        val user = User(id = 1L, email = "user@test.com", passwordHash = "hash", name = "User", 
                       profileImageId = 100L, company = company)
        
        every { fileClient.deleteMyProfileImage() } returns Unit
        every { userRepository.save(any()) } returns user

        // when
        val result = userService.deleteMyProfileImage(user)

        // then
        assertNull(user.profileImageId)
        verify { fileClient.deleteMyProfileImage() }
        verify { userRepository.save(user) }
    }

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
        every { userRepository.save(any()) } returns user

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
        every { userRepository.save(any()) } returns user

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
        every { userRepository.save(any()) } returns user

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
        every { userRepository.save(any()) } returns user

        // when
        userService.updateUserProfile(1L, user, request, mockFile)

        // then
        assertEquals("Updated", user.name)
        verify(exactly = 0) { fileClient.uploadProfileImage(any(), any()) }
        verify { userRepository.save(user) }
    }
}
