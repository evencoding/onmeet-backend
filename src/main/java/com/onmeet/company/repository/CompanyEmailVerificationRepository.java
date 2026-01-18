package com.onmeet.company.repository;

import com.onmeet.company.entity.company.CompanyEmailVerification;
import com.onmeet.company.entity.company.CompanyEmailVerificationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyEmailVerificationRepository extends JpaRepository<CompanyEmailVerification, String> {
    Optional<CompanyEmailVerification> findByToken(String token);

    Optional<CompanyEmailVerification> findByEmailAndStatus(String email, CompanyEmailVerificationStatus status);
}
