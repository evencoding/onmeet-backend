package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    @EntityGraph(attributePaths = ["company", "teamMemberships", "jobTitle", "roles"])
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean

    @EntityGraph(attributePaths = ["company", "teamMemberships", "jobTitle"])
    fun findByCompany(company: Company, pageable: Pageable): Page<User>
}
