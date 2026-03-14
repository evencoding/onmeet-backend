package com.onmeet.auth.integration

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.CompanyRepository
import com.onmeet.auth.repository.jpa.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

/**
 * UserRepository 통합 테스트.
 * 실제 MySQL 컨테이너를 사용하여 JPA 쿼리가 정상 동작하는지 검증합니다.
 * Docker가 실행 중이지 않으면 자동으로 스킵됩니다.
 */
@Transactional
@EnabledIf("isDockerAvailable")
class UserRepositoryIntegrationTest : BaseIntegrationTest() {

    companion object {
        @JvmStatic
        fun isDockerAvailable(): Boolean {
            return try {
                val process = ProcessBuilder("docker", "info").start()
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0
            } catch (e: Exception) {
                false
            }
        }
    }

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Test
    fun `should save and retrieve user from database`() {
        // given: 회사와 사용자 엔티티 생성
        val company = Company(name = "Test Company")
        val savedCompany = companyRepository.save(company)

        val user = User(
            email = "test@test.com",
            passwordHash = "hashed_password",
            name = "Test User",
            company = savedCompany
        )

        // when: 사용자를 DB에 저장
        val savedUser = userRepository.save(user)

        // then: 저장된 사용자가 올바르게 조회되는지 검증
        assertNotNull(savedUser.id)
        assertEquals("test@test.com", savedUser.email)
        assertEquals("Test User", savedUser.name)
        assertEquals(savedCompany.id, savedUser.company.id)

        // when: ID로 사용자 조회
        val foundUser = userRepository.findById(savedUser.id!!).orElse(null)

        // then: 조회된 사용자가 저장한 사용자와 일치하는지 검증
        assertNotNull(foundUser)
        assertEquals(savedUser.id, foundUser.id)
        assertEquals(savedUser.email, foundUser.email)
    }

    @Test
    fun `should find user by email`() {
        // given: 회사와 사용자 생성 및 저장
        val company = Company(name = "Email Test Co")
        val savedCompany = companyRepository.save(company)

        val user = User(
            email = "find@email.com",
            passwordHash = "hash",
            name = "Findable User",
            company = savedCompany
        )
        val savedUser = userRepository.save(user)

        // when: 이메일로 사용자 조회
        val found = userRepository.findByEmail("find@email.com")

        // then: 조회된 사용자가 올바른지 검증
        assertTrue(found.isPresent)
        assertEquals(savedUser.id, found.get().id)
        assertEquals("find@email.com", found.get().email)
    }

    @Test
    fun `should delete user from database`() {
        // given: 사용자 생성 및 저장
        val company = Company(name = "Delete Test Co")
        val savedCompany = companyRepository.save(company)

        val user = User(
            email = "delete@test.com",
            passwordHash = "hash",
            name = "Delete User",
            company = savedCompany
        )
        val savedUser = userRepository.save(user)
        val userId = savedUser.id!!

        // when: 사용자 삭제
        userRepository.deleteById(userId)

        // then: 삭제된 사용자가 조회되지 않는지 검증
        val deletedUser = userRepository.findById(userId).orElse(null)
        assertNull(deletedUser)
    }
}
