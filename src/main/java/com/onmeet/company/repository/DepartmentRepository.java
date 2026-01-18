package com.onmeet.company.repository;

import com.onmeet.company.entity.department.Department;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, String> {
    List<Department> findByCompanyIdOrderByNameAsc(String companyId);

    Optional<Department> findByIdAndCompanyId(String id, String companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(String companyId, String name);
}
