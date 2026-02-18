package com.onmeet.auth.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "teams")
@EntityListeners(AuditingEntityListener::class)
class Team(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column
    var description: String? = null,

    @Column
    var color: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    var company: Company,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id")
    var leader: User? = null,

    @OneToMany(mappedBy = "team", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var members: MutableSet<TeamMember> = mutableSetOf(),

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: TeamStatus = TeamStatus.ACTIVE,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null
) {
    enum class TeamStatus {
        ACTIVE, INACTIVE, PENDING_APPROVAL
    }

    fun isLeader(user: User): Boolean = leader?.id == user.id

    fun isActive(): Boolean = status == TeamStatus.ACTIVE

    fun belongsToCompany(companyId: Long): Boolean = company.id == companyId

    fun requireId(): Long = id ?: throw IllegalStateException("Team ID is required but was null")
}
