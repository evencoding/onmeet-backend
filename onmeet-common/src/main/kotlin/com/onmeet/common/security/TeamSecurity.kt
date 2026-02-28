package com.onmeet.common.security

import com.onmeet.common.dto.SecurityCheckResponse

/**
 * 팀 관련 권한을 검증하는 공통 인터페이스.
 * auth-service에서는 로컬 DB를 사용하여 구현하고,
 * 타 서비스에서는 Feign Client를 사용하여 auth-service에 요청하는 방식으로 구현할 수 있습니다.
 */
interface TeamSecurity {
    fun isLeaderOf(teamId: Long, principal: Any?): Boolean
    fun isMemberOf(teamId: Long, principal: Any?): Boolean
    fun belongsToSameCompany(teamId: Long, principal: Any?): Boolean
}
