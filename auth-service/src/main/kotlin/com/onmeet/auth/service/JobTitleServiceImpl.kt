package com.onmeet.auth.service

import com.onmeet.auth.dto.JobTitleRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.JobTitle
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.*
import com.onmeet.common.exception.BusinessException
import com.onmeet.common.exception.errorcode.AuthErrorCode
import com.onmeet.auth.repository.jpa.JobTitleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class JobTitleServiceImpl(
    private val jobTitleRepository: JobTitleRepository
) : JobTitleService {

    override fun getJobTitles(company: Company): List<JobTitle> =
        jobTitleRepository.findAllByCompany(company)

    @Transactional
    override fun createJobTitle(manager: User, request: JobTitleRequest): JobTitle {
        validateManager(manager)
        
        val existingTitles = jobTitleRepository.findAllByCompany(manager.company)
        
        val jobTitle = JobTitle(
            name = request.name,
            company = manager.company,
            isDefault = existingTitles.isEmpty() || request.isDefault
        )

        if (jobTitle.isDefault) unsetDefaultFlags(manager.company)

        return jobTitleRepository.save(jobTitle)
    }

    @Transactional
    override fun updateJobTitle(manager: User, id: Long, request: JobTitleRequest): JobTitle {
        validateManager(manager)
        val jobTitle = jobTitleRepository.findById(id)
            // TODO: [AUTH][AuthErrorCode.JOB_TITLE_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.JOB_TITLE_NOT_FOUND) }

        if (jobTitle.company.requireId() != manager.company.requireId()) {
            // TODO: [AUTH][AuthErrorCode.JOB_TITLE_CROSS_COMPANY] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.JOB_TITLE_CROSS_COMPANY)
        }

        jobTitle.name = request.name

        if (request.isDefault && !jobTitle.isDefault) {
            unsetDefaultFlags(manager.company)
            jobTitle.isDefault = true
        } else if (!request.isDefault && jobTitle.isDefault) {
            val hasOtherDefault = jobTitleRepository.findAllByCompany(manager.company)
                .any { it.id != jobTitle.id && it.isDefault }
            if (!hasOtherDefault) {
                // TODO: [AUTH][AuthErrorCode.JOB_TITLE_DEFAULT_REQUIRED] 에러메시지 검수 요청
                throw BusinessException(AuthErrorCode.JOB_TITLE_DEFAULT_REQUIRED)
            }
            jobTitle.isDefault = false
        }

        return jobTitleRepository.save(jobTitle)
    }

    @Transactional
    override fun deleteJobTitle(manager: User, id: Long) {
        validateManager(manager)
        val jobTitle = jobTitleRepository.findById(id)
            // TODO: [AUTH][AuthErrorCode.JOB_TITLE_NOT_FOUND] 에러메시지 검수 요청
            .orElseThrow { BusinessException(AuthErrorCode.JOB_TITLE_NOT_FOUND) }

        if (jobTitle.company.requireId() != manager.company.requireId()) {
            // TODO: [AUTH][AuthErrorCode.JOB_TITLE_CROSS_COMPANY] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.JOB_TITLE_CROSS_COMPANY)
        }

        if (jobTitle.isDefault) {
            val hasOtherDefault = jobTitleRepository.findAllByCompany(manager.company)
                .any { it.id != jobTitle.id && it.isDefault }
            if (!hasOtherDefault) {
                // TODO: [AUTH][AuthErrorCode.JOB_TITLE_DEFAULT_REQUIRED] 에러메시지 검수 요청
                throw BusinessException(AuthErrorCode.JOB_TITLE_DEFAULT_REQUIRED)
            }
        }

        jobTitleRepository.delete(jobTitle)
    }

    @Transactional
    override fun createDefaultInitialTitle(company: Company): JobTitle =
        jobTitleRepository.save(
            JobTitle(
                name = "선택 안함",
                company = company,
                isDefault = true
            )
        )

    override fun getDefaultJobTitle(company: Company): JobTitle? =
        jobTitleRepository.findByCompanyAndIsDefaultTrue(company)

    override fun getJobTitleByName(company: Company, name: String): JobTitle? =
        jobTitleRepository.findByCompanyAndName(company, name)

    private fun validateManager(user: User) {
        if (!user.hasRole(User.Role.MANAGER)) {
            // TODO: [AUTH][AuthErrorCode.JOB_TITLE_MANAGE_FORBIDDEN] 에러메시지 검수 요청
            throw BusinessException(AuthErrorCode.JOB_TITLE_MANAGE_FORBIDDEN)
        }
    }

    private fun unsetDefaultFlags(company: Company) {
        jobTitleRepository.findAllByCompany(company)
            .filter { it.isDefault }
            .forEach { it.isDefault = false }
    }
}
