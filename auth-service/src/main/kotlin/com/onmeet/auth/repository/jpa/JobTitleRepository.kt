package com.onmeet.auth.repository.jpa

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.JobTitle
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JobTitleRepository : JpaRepository<JobTitle, Long> {
    fun findAllByCompany(company: Company): List<JobTitle>
    fun findByCompanyAndIsDefaultTrue(company: Company): JobTitle?
}
