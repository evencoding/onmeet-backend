package com.onmeet.auth.service

import com.onmeet.auth.dto.CompanyRequest
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team
import com.onmeet.auth.exception.*
import com.onmeet.auth.repository.jpa.CompanyRepository
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
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
            // TODO: [AUTH][AuthErrorCode.COMPANY_ALREADY_EXISTS] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.COMPANY_ALREADY_EXISTS)
        } else {
            companyRepository.save(Company(name = name))
        }

    override fun getCompany(id: Long): Company =
        companyRepository.findById(id)
            // TODO: [AUTH][AuthErrorCode.COMPANY_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.COMPANY_NOT_FOUND) }

    override fun getTeam(id: Long): Team =
        teamRepository.findById(id)
            // TODO: [AUTH][AuthErrorCode.TEAM_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.TEAM_NOT_FOUND) }
}
