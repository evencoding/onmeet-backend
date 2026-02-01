package com.onmeet.auth.service

import com.onmeet.auth.dto.CompanyRequest
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team
import com.onmeet.auth.repository.CompanyRepository
import com.onmeet.auth.repository.TeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CompanyService(
    private val companyRepository: CompanyRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: com.onmeet.auth.repository.UserRepository
) {

    @Transactional
    fun createCompany(name: String): Company {
        if (companyRepository.findByName(name) != null) {
            throw IllegalArgumentException("Company already exists")
        }
        return companyRepository.save(Company(name = name))
    }

    @Transactional
    fun createTeam(companyId: Long, request: TeamRequest): Team {
        val company = companyRepository.findById(companyId)
            .orElseThrow { IllegalArgumentException("Company not found") }

        if (teamRepository.findByNameAndCompanyId(request.name, companyId) != null) {
            throw IllegalArgumentException("Team already exists in this company")
        }

        return teamRepository.save(
            Team(
                name = request.name,
                description = request.description,
                color = request.color,
                company = company
            )
        )
    }

    fun getCompany(id: Long): Company {
        return companyRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Company not found") }
    }
    
    fun getTeam(id: Long): Team {
        return teamRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Team not found") }
    }
}
