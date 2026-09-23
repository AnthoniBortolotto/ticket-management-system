package com.ticketsystem.team.infra;

import com.ticketsystem.team.domain.Team;
import com.ticketsystem.team.domain.TeamRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapta o Spring Data a interface de dominio. Trocar o armazenamento e trocar esta classe. */
@Repository
class JpaTeamRepository implements TeamRepository {

    private final SpringDataTeamRepository springData;

    JpaTeamRepository(SpringDataTeamRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<Team> findById(Long id) {
        return springData.findById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return springData.existsByNameIgnoreCase(name);
    }

    @Override
    public Team save(Team team) {
        return springData.save(team);
    }
}
