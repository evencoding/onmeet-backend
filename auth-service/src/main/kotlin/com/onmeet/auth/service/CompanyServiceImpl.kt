package com.onmeet.auth.service

import com.onmeet.auth.dto.CompanyRequest
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.CompanyRepository
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CompanyServiceImpl(
    private val companyRepository: CompanyRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) : CompanyService {

    @Transactional
    override fun createCompany(name: String): Company =
        if (companyRepository.findByName(name) != null) {
            throw CompanyAlreadyExistsException("Company already exists with name: $name")
        } else {
            companyRepository.save(Company(name = name))
        }

    override fun getCompany(id: Long): Company =
        companyRepository.findById(id)
            .orElseThrow { CompanyNotFoundException("Company not found: $id") }
    
    override fun getTeam(id: Long): Team =
        teamRepository.findById(id)
            .orElseThrow { TeamNotFoundException("Team not found: $id") }
}
