package com.ticketsystem.team.infra;

import com.ticketsystem.team.domain.TeamMembership;
import com.ticketsystem.team.domain.TeamMembershipRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapta o Spring Data a interface de dominio. Trocar o armazenamento e trocar esta classe. */
@Repository
class JpaTeamMembershipRepository implements TeamMembershipRepository {

    private final SpringDataTeamMembershipRepository springData;

    JpaTeamMembershipRepository(SpringDataTeamMembershipRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<TeamMembership> find(Long teamId, Long userId) {
        return springData.findByTeamIdAndUserId(teamId, userId);
    }

    @Override
    public List<TeamMembership> findByTeam(Long teamId) {
        return springData.findByTeamIdOrderByIdAsc(teamId);
    }

    @Override
    public TeamMembership save(TeamMembership membership) {
        return springData.save(membership);
    }

    @Override
    public void delete(TeamMembership membership) {
        springData.delete(membership);
    }
}
