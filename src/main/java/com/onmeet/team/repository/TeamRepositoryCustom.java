package com.onmeet.team.repository;

import com.onmeet.team.entity.Team;
import java.util.List;

public interface TeamRepositoryCustom {
    List<Team> searchByName(String keyword);
}
