package com.ticketsystem.team.service;

import com.ticketsystem.team.domain.Team;
import com.ticketsystem.team.domain.TeamMembership;
import java.util.List;

/** Uma equipe e os vinculos dela, lidos na mesma transacao. */
public record TeamDetails(Team team, List<TeamMembership> members) {

    public TeamDetails {
        members = List.copyOf(members);
    }
}
