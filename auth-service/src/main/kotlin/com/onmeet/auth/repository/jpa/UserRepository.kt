package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {
    @EntityGraph(attributePaths = ["company", "teamMemberships", "jobTitle", "roles"])
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean

    @EntityGraph(attributePaths = ["company", "teamMemberships", "jobTitle"])
    fun findByCompany(company: Company, pageable: Pageable): Page<User>

    @Query("SELECT u FROM User u WHERE u.company.id = :companyId AND :role MEMBER OF u.roles")
    fun findByCompanyIdAndRole(@Param("companyId") companyId: Long, @Param("role") role: User.Role): List<User>
}
