package com.onmeet.auth.service

import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team

interface CompanyService {
    fun createCompany(name: String): Company
    fun getCompany(id: Long): Company
    fun getTeam(id: Long): Team
}
