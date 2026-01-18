package com.onmeet.company.repository;

import com.onmeet.company.entity.employee.Employee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
    Optional<Employee> findByUserIdAndCompanyId(String userId, String companyId);
}
