package com.onmeet.auth.dto

import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Invitation
import com.onmeet.auth.entity.JobTitle
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.User

fun User.toResponseDto() = UserResponseDto(
    id = this.requireId(),
    email = this.email,
    name = this.name,
    employeeId = this.employeeId,
    roles = this.roles.map { it.name }.toSet(),
    status = this.status.name,
    company = this.company.toInfoDto(),
    jobTitle = this.jobTitle?.toResponseDto(),
    teams = this.teams.map { it.toInfoDto() }
)

fun Company.toInfoDto() = CompanyInfoDto(
    id = this.requireId(),
    name = this.name
)

fun Team.toInfoDto() = TeamInfoDto(
    id = this.requireId(),
    name = this.name,
    color = this.color
)

fun JobTitle.toResponseDto() = JobTitleResponse(
    id = this.requireId(),
    name = this.name,
    isDefault = this.isDefault
)

fun Invitation.toResponseDto() = InvitationResponse(
    email = this.email,
    companyName = this.company.name,
    role = this.role
)
