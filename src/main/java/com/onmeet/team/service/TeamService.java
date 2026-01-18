package com.onmeet.team.service;

import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
import com.onmeet.company.entity.company.Company;
import com.onmeet.company.entity.company.CompanyStatus;
import com.onmeet.company.repository.CompanyRepository;
import com.onmeet.team.dto.TeamCreateRequest;
import com.onmeet.team.dto.TeamResponse;
import com.onmeet.team.entity.Team;
import com.onmeet.team.repository.TeamRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final CompanyRepository companyRepository;

    public TeamService(TeamRepository teamRepository, CompanyRepository companyRepository) {
        this.teamRepository = teamRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public TeamResponse create(TeamCreateRequest request) {
        Company company = getActiveCompany(request.companyId());
        Team team = new Team(company, request.name());
        Team saved = teamRepository.save(team);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TeamResponse get(String teamId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "팀을 찾을 수 없습니다"));
        return toResponse(team);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> search(String keyword) {
        return teamRepository.searchByName(keyword).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listByCompany(String companyId) {
        getActiveCompany(companyId);
        return teamRepository.findByCompanyIdOrderByNameAsc(companyId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private TeamResponse toResponse(Team team) {
        return new TeamResponse(
            team.getId(),
            team.getCompany().getId(),
            team.getName(),
            team.getCreatedAt(),
            team.getUpdatedAt()
        );
    }

    private Company getActiveCompany(String companyId) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "회사를 찾을 수 없습니다"));
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new BizException(ErrorCode.FORBIDDEN, "비활성화된 회사입니다");
        }
        return company;
    }
}
