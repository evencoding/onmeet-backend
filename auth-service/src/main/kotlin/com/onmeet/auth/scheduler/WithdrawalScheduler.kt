package com.onmeet.auth.scheduler

import com.onmeet.auth.repository.jpa.WithdrawnUserRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class WithdrawalScheduler(
    private val withdrawnUserRepository: WithdrawnUserRepository
) {

    // Runs every day at 3:00 AM
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun deleteExpiredWithdrawnUsers() {
        // Retention period: 1 year (can be configured via properties)
        val retentionPeriodEndDate = LocalDateTime.now().minusYears(1)
        
        withdrawnUserRepository.deleteAllByWithdrawnAtBefore(retentionPeriodEndDate)
        
        // Logging or monitoring can be added here
        println("Executed scheduled deletion of withdrawn users older than $retentionPeriodEndDate")
    }
}
