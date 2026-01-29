package com.onmeet.auth.repository

import com.onmeet.auth.entity.Company
import org.springframework.data.jpa.repository.JpaRepository

interface CompanyRepository : JpaRepository<Company, Long> {
    fun findByName(name: String): Company?
}
