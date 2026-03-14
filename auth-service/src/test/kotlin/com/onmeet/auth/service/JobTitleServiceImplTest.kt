package com.onmeet.auth.service

import com.onmeet.auth.dto.JobTitleRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.JobTitle
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.JobTitleRepository
import com.onmeet.common.exception.BusinessException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
class JobTitleServiceImplTest {

    @MockK
    private lateinit var jobTitleRepository: JobTitleRepository

    @InjectMockKs
    private lateinit var jobTitleService: JobTitleServiceImpl

    private val company = Company(id = 1L, name = "Test Company")
    private val manager = User(
        id = 1L,
        email = "manager@test.com",
        passwordHash = "hash",
        name = "Manager",
        company = company,
        roles = mutableSetOf(User.Role.USER, User.Role.MANAGER),
        status = User.UserStatus.ACTIVE
    )
    private val nonManager = User(
        id = 2L,
        email = "user@test.com",
        passwordHash = "hash",
        name = "User",
        company = company,
        roles = mutableSetOf(User.Role.USER),
        status = User.UserStatus.ACTIVE
    )

    @Test
    fun `createJobTitle should create new job title for manager`() {
        // given
        val request = JobTitleRequest(name = "Engineer", isDefault = false)
        val jobTitle = JobTitle(id = 1L, name = "Engineer", company = company, isDefault = false)
        every { jobTitleRepository.findAllByCompany(company) } returns emptyList()
        every { jobTitleRepository.save(any()) } returns jobTitle

        // when
        val result = jobTitleService.createJobTitle(manager, request)

        // then
        assertEquals("Engineer", result.name)
        verify { jobTitleRepository.save(any()) }
    }

    @Test
    fun `createJobTitle should throw BusinessException when user is not manager`() {
        // given
        val request = JobTitleRequest(name = "Engineer", isDefault = false)

        // when & then
        assertThrows<BusinessException> {
            jobTitleService.createJobTitle(nonManager, request)
        }
        verify(exactly = 0) { jobTitleRepository.save(any()) }
    }

    @Test
    fun `createJobTitle should set isDefault true when creating first title`() {
        // given
        val request = JobTitleRequest(name = "First Title", isDefault = false)
        val savedTitle = JobTitle(id = 1L, name = "First Title", company = company, isDefault = true)
        every { jobTitleRepository.findAllByCompany(company) } returns emptyList()
        every { jobTitleRepository.save(any()) } returns savedTitle

        // when
        val result = jobTitleService.createJobTitle(manager, request)

        // then
        assertTrue(result.isDefault)
    }

    @Test
    fun `deleteJobTitle should throw BusinessException when deleting only default title`() {
        // given
        val defaultTitle = JobTitle(id = 1L, name = "선택 안함", company = company, isDefault = true)
        every { jobTitleRepository.findById(1L) } returns Optional.of(defaultTitle)
        every { jobTitleRepository.findAllByCompany(company) } returns listOf(defaultTitle)

        // when & then
        assertThrows<BusinessException> {
            jobTitleService.deleteJobTitle(manager, 1L)
        }
        verify(exactly = 0) { jobTitleRepository.delete(any()) }
    }

    @Test
    fun `deleteJobTitle should throw BusinessException when title belongs to different company`() {
        // given
        val otherCompany = Company(id = 2L, name = "Other Company")
        val otherTitle = JobTitle(id = 2L, name = "Manager", company = otherCompany, isDefault = false)
        every { jobTitleRepository.findById(2L) } returns Optional.of(otherTitle)

        // when & then
        assertThrows<BusinessException> {
            jobTitleService.deleteJobTitle(manager, 2L)
        }
    }

    @Test
    fun `deleteJobTitle should throw BusinessException when non-manager tries to delete`() {
        // given - no repo call needed, fails before lookup

        // when & then
        assertThrows<BusinessException> {
            jobTitleService.deleteJobTitle(nonManager, 1L)
        }
    }

    @Test
    fun `deleteJobTitle should succeed when there is another default title`() {
        // given
        val defaultTitle = JobTitle(id = 1L, name = "Staff", company = company, isDefault = true)
        val anotherDefault = JobTitle(id = 2L, name = "Engineer", company = company, isDefault = true)
        every { jobTitleRepository.findById(1L) } returns Optional.of(defaultTitle)
        every { jobTitleRepository.findAllByCompany(company) } returns listOf(defaultTitle, anotherDefault)
        every { jobTitleRepository.delete(defaultTitle) } returns Unit

        // when
        jobTitleService.deleteJobTitle(manager, 1L)

        // then
        verify { jobTitleRepository.delete(defaultTitle) }
    }

    @Test
    fun `createDefaultInitialTitle should save title with isDefault true`() {
        // given
        val saved = JobTitle(id = 1L, name = "선택 안함", company = company, isDefault = true)
        every { jobTitleRepository.save(any()) } returns saved

        // when
        val result = jobTitleService.createDefaultInitialTitle(company)

        // then
        assertEquals("선택 안함", result.name)
        assertTrue(result.isDefault)
    }

    @Test
    fun `getDefaultJobTitle should return null when no default exists`() {
        // given
        every { jobTitleRepository.findByCompanyAndIsDefaultTrue(company) } returns null

        // when
        val result = jobTitleService.getDefaultJobTitle(company)

        // then
        assertNull(result)
    }
}
