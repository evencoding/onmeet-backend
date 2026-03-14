package com.onmeet.auth.service

import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.User

interface TeamCreationStrategy {
    fun create(user: User, request: TeamRequest): Team
}
