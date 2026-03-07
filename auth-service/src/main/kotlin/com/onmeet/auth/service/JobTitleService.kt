package com.onmeet.auth.service

import com.onmeet.auth.dto.JobTitleRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.JobTitle
import com.onmeet.auth.entity.User

interface JobTitleService {
    fun getJobTitles(company: Company): List<JobTitle>
    fun createJobTitle(manager: User, request: JobTitleRequest): JobTitle
    fun updateJobTitle(manager: User, id: Long, request: JobTitleRequest): JobTitle
    fun deleteJobTitle(manager: User, id: Long)
    fun createDefaultInitialTitle(company: Company): JobTitle
    fun getDefaultJobTitle(company: Company): JobTitle?
    fun getJobTitleByName(company: Company, name: String): JobTitle?
}
