package com.ticketsystem.team.web.v1.dto;

import com.ticketsystem.team.domain.TeamMembership;
import com.ticketsystem.team.domain.TeamRole;

/**
 * Um vinculo como a API o mostra.
 *
 * <p>So o id da pessoa, sem nome nem e-mail: montar esses campos exigiria uma consulta a
 * {@code user} por membro. Campo novo na resposta nao quebra contrato, entao eles entram
 * quando a tela de equipes da Fase 8 precisar — e junto com uma consulta em lote.
 */
public record TeamMemberResponse(Long userId, TeamRole role) {

    public static TeamMemberResponse from(TeamMembership vinculo) {
        return new TeamMemberResponse(vinculo.getUserId(), vinculo.getRole());
    }
}
