package com.onmeet.company.repository;

import com.onmeet.company.entity.employee.EmployeeInvite;
import com.onmeet.company.entity.employee.EmployeeInviteStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeInviteRepository extends JpaRepository<EmployeeInvite, String> {
    Optional<EmployeeInvite> findByToken(String token);

    Optional<EmployeeInvite> findByCompanyIdAndEmailAndStatus(String companyId, String email, EmployeeInviteStatus status);
}
