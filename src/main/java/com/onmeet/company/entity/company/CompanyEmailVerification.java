package com.onmeet.company.entity.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "company_email_verifications")
@EntityListeners(AuditingEntityListener.class)
public class CompanyEmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "char(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(nullable = false, length = 255)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_size", nullable = false, length = 20)
    private CompanySize companySize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyEmailVerificationStatus status;

    @Column(nullable = false, length = 36, unique = true)
    private String token;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public CompanyEmailVerification(
        String email,
        String companyName,
        String domain,
        CompanySize companySize,
        String token,
        Instant expiresAt
    ) {
        this.email = email;
        this.companyName = companyName;
        this.domain = domain;
        this.companySize = companySize;
        this.token = token;
        this.expiresAt = expiresAt;
        this.status = CompanyEmailVerificationStatus.PENDING;
    }

    public void markVerified(Instant verifiedAt) {
        this.status = CompanyEmailVerificationStatus.VERIFIED;
        this.verifiedAt = verifiedAt;
    }

    public void markExpired(Instant expiredAt) {
        this.status = CompanyEmailVerificationStatus.EXPIRED;
        this.expiresAt = expiredAt;
    }

    public void markConsumed(Instant consumedAt) {
        this.status = CompanyEmailVerificationStatus.CONSUMED;
        this.verifiedAt = consumedAt;
    }
}
