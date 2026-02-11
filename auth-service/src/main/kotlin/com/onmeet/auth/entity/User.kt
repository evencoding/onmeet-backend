package com.onmeet.auth.entity

import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener::class)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false)
    var passwordHash: String,

    @Column(nullable = false)
    var name: String,

    @Column
    var employeeId: String? = null, // 사번

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    var company: Company,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_title_id")
    var jobTitle: JobTitle? = null,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_teams",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "team_id")]
    )
    var teams: MutableSet<Team> = mutableSetOf(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.ACTIVE,

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "role")
    var roles: MutableSet<Role> = mutableSetOf(Role.USER),

    @Column
    var profileImageId: Long? = null,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null
) : UserDetails {

    enum class UserStatus {
        ACTIVE, INACTIVE, INVITED
    }

    enum class Role {
        USER, ADMIN, MANAGER, TEAM_LEADER
    }

    fun hasRole(role: Role): Boolean = roles.contains(role)

    fun isManager(): Boolean = hasRole(Role.MANAGER)
    
    fun isTeamLeader(): Boolean = hasRole(Role.TEAM_LEADER)

    fun isSelf(user: User): Boolean = this.id == user.id

    fun belongsToCompany(companyId: Long): Boolean = company.id == companyId

    fun requireId(): Long = id ?: throw IllegalStateException("User ID is required but was null")

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
        roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }.toMutableList()

    override fun getPassword(): String = passwordHash

    override fun getUsername(): String = email

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    fun activate() {
        this.status = UserStatus.ACTIVE
    }

    fun deactivate() {
        this.status = UserStatus.INACTIVE
    }

    override fun isEnabled(): Boolean = (this.status == UserStatus.ACTIVE)
}
