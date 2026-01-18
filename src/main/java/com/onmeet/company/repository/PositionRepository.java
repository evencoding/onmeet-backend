package com.onmeet.company.repository;

import com.onmeet.company.entity.position.Position;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, String> {
    List<Position> findByCompanyIdOrderByNameAsc(String companyId);

    Optional<Position> findByIdAndCompanyId(String id, String companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(String companyId, String name);
}
