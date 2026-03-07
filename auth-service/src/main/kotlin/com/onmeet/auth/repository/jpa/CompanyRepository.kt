package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.Company
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CompanyRepository : JpaRepository<Company, Long> {
    fun findByName(name: String): Company?
}
