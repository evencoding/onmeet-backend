package com.onmeet.team.controller;

import com.onmeet.common.response.ApiResponse;
import com.onmeet.team.dto.TeamCreateRequest;
import com.onmeet.team.dto.TeamResponse;
import com.onmeet.team.service.TeamService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ApiResponse<TeamResponse> create(@Valid @RequestBody TeamCreateRequest request) {
        return ApiResponse.ok(teamService.create(request));
    }

    @GetMapping("/{teamId}")
    public ApiResponse<TeamResponse> get(@PathVariable Long teamId) {
        return ApiResponse.ok(teamService.get(teamId));
    }

    @GetMapping
    public ApiResponse<List<TeamResponse>> list(@RequestParam(required = false) String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.ok(teamService.list());
        }
        return ApiResponse.ok(teamService.search(keyword));
    }
}
