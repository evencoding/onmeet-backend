package com.onmeet.team.service;

import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
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

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Transactional
    public TeamResponse create(TeamCreateRequest request) {
        Team team = new Team(request.name());
        Team saved = teamRepository.save(team);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TeamResponse get(Long teamId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Team not found"));
        return toResponse(team);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> search(String keyword) {
        return teamRepository.searchByName(keyword).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> list() {
        return teamRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private TeamResponse toResponse(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getCreatedAt(), team.getUpdatedAt());
    }
}
