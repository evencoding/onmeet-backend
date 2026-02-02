package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = ["company", "teams"])
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
}
