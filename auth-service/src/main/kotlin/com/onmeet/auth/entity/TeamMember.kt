package com.onmeet.auth.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "team_members")
@EntityListeners(AuditingEntityListener::class)
class TeamMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    var team: Team,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var role: TeamRole = TeamRole.MEMBER,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var joinedAt: LocalDateTime? = null
) {
    enum class TeamRole {
        LEADER, MEMBER
    }

    fun isLeader(): Boolean = role == TeamRole.LEADER

    fun isMember(): Boolean = role == TeamRole.MEMBER
}
