package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.Team
import org.springframework.data.jpa.repository.JpaRepository

interface TeamRepository : JpaRepository<Team, Long> {
    fun findByNameAndCompanyId(name: String, companyId: Long): Team?
    fun findAllByCompanyId(companyId: Long): List<Team>
}
