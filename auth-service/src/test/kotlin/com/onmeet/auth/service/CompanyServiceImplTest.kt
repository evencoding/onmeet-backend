package com.onmeet.auth.service

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team
import com.onmeet.auth.repository.jpa.CompanyRepository
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
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
class CompanyServiceImplTest {

    @MockK
    private lateinit var companyRepository: CompanyRepository

    @MockK
    private lateinit var teamRepository: TeamRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var companyService: CompanyServiceImpl

    @Test
    fun `createCompany should create and return new company`() {
        // given
        val company = Company(id = 1L, name = "Test Corp")
        every { companyRepository.findByName("Test Corp") } returns null
        every { companyRepository.save(any()) } returns company

        // when
        val result = companyService.createCompany("Test Corp")

        // then
        assertEquals("Test Corp", result.name)
        verify { companyRepository.save(any()) }
    }

    @Test
    fun `createCompany should throw BusinessException when company name already exists`() {
        // given
        val existing = Company(id = 1L, name = "Duplicate Corp")
        every { companyRepository.findByName("Duplicate Corp") } returns existing

        // when & then
        assertThrows<BusinessException> {
            companyService.createCompany("Duplicate Corp")
        }
        verify(exactly = 0) { companyRepository.save(any()) }
    }

    @Test
    fun `getCompany should return company for valid id`() {
        // given
        val company = Company(id = 1L, name = "Test Corp")
        every { companyRepository.findById(1L) } returns Optional.of(company)

        // when
        val result = companyService.getCompany(1L)

        // then
        assertEquals(1L, result.id)
        assertEquals("Test Corp", result.name)
    }

    @Test
    fun `getCompany should throw BusinessException when company not found`() {
        // given
        every { companyRepository.findById(999L) } returns Optional.empty()

        // when & then
        assertThrows<BusinessException> {
            companyService.getCompany(999L)
        }
    }

    @Test
    fun `getTeam should return team for valid id`() {
        // given
        val company = Company(id = 1L, name = "Test Corp")
        val team = Team(id = 1L, name = "Dev Team", company = company)
        every { teamRepository.findById(1L) } returns Optional.of(team)

        // when
        val result = companyService.getTeam(1L)

        // then
        assertEquals(1L, result.id)
        assertEquals("Dev Team", result.name)
    }

    @Test
    fun `getTeam should throw BusinessException when team not found`() {
        // given
        every { teamRepository.findById(999L) } returns Optional.empty()

        // when & then
        assertThrows<BusinessException> {
            companyService.getTeam(999L)
        }
    }
}
