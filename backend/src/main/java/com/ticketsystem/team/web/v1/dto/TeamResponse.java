package com.ticketsystem.team.web.v1.dto;

import com.ticketsystem.team.domain.Team;
import com.ticketsystem.team.domain.TeamMembership;
import java.util.List;

/** Uma equipe e quem participa dela, na ordem em que entrou. */
public record TeamResponse(Long id, String name, String description, List<TeamMemberResponse> members) {

    public static TeamResponse from(Team equipe, List<TeamMembership> vinculos) {
        return new TeamResponse(
                equipe.getId(),
                equipe.getName(),
                equipe.getDescription(),
                vinculos.stream().map(TeamMemberResponse::from).toList());
    }
}
