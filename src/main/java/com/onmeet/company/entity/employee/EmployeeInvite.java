package com.onmeet.company.entity.employee;

import com.onmeet.company.entity.position.Position;
import com.onmeet.company.entity.company.Company;
import com.onmeet.company.entity.department.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "employee_invites")
@EntityListeners(AuditingEntityListener.class)
public class EmployeeInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "char(36)", updatable = false, nullable = false)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", columnDefinition = "char(36)")
    private Company company;

    @Column(nullable = false, length = 255)
    private String email;

    @ManyToOne
    @JoinColumn(name = "department_id", columnDefinition = "char(36)")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "position_id", columnDefinition = "char(36)")
    private Position position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeRole role;

    @Column(name = "employee_no", length = 50)
    private String employeeNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeInviteStatus status;

    @Column(nullable = false, length = 36, unique = true)
    private String token;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    public EmployeeInvite(
        Company company,
        String email,
        Department department,
        Position position,
        EmployeeRole role,
        String employeeNo,
        String token,
        Instant expiresAt
    ) {
        this.company = company;
        this.email = email;
        this.department = department;
        this.position = position;
        this.role = role;
        this.employeeNo = employeeNo;
        this.token = token;
        this.status = EmployeeInviteStatus.INVITED;
        this.expiresAt = expiresAt;
    }

    public void markAccepted(Instant acceptedAt) {
        this.status = EmployeeInviteStatus.ACCEPTED;
        this.acceptedAt = acceptedAt;
    }

    public void markExpired(Instant expiredAt) {
        this.status = EmployeeInviteStatus.EXPIRED;
        this.expiresAt = expiredAt;
    }
}
