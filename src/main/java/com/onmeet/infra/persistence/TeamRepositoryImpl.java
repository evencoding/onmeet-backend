package com.onmeet.infra.persistence;

import com.onmeet.team.entity.Team;
import com.onmeet.team.entity.QTeam;
import com.onmeet.team.repository.TeamRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class TeamRepositoryImpl implements TeamRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public TeamRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<Team> searchByName(String keyword) {
        QTeam team = QTeam.team;
        return queryFactory
            .selectFrom(team)
            .where(team.name.containsIgnoreCase(keyword))
            .fetch();
    }
}
