package com.onmeet.team.repository;

import com.onmeet.team.entity.Team;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, String>, TeamRepositoryCustom {
    List<Team> findByCompanyIdOrderByNameAsc(String companyId);
}
