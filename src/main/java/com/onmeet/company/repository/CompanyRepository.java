package com.onmeet.company.repository;

import com.onmeet.company.entity.Company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, String> {
    Optional<Company> findByDomain(String domain);
}
