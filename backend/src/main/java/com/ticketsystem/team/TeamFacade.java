package com.ticketsystem.team;

import com.ticketsystem.team.service.TeamService;
import org.springframework.stereotype.Component;

/**
 * A unica porta do modulo {@code team}.
 *
 * <p>Responde perguntas que bloqueiam uma decisao agora — "esta pessoa participa desta
 * equipe?", "lidera?" — e e por isso chamada direto, e nao por evento. {@code ticket} vai
 * consumi-la para decidir visibilidade e reatribuicao.
 *
 * <p>As respostas vem do banco a cada chamada, nunca do token: tirar alguem de uma equipe
 * vale na proxima requisicao, e nao quando o token vencer (ADR 0003).
 *
 * <p>A ordem dos parametros e sempre {@code (teamId, userId)}, como nas URLs e no
 * repositorio. Sao dois {@code Long}; uma troca compila e responde sobre a pessoa errada.
 *
 * <p>Ainda faltam as perguntas em conjunto — "de quais equipes esta pessoa participa?" —
 * que o filtro de listagem de tickets vai precisar. Entram na Fase 5, com o primeiro
 * consumidor.
 */
@Component
public class TeamFacade {

    private final TeamService service;

    TeamFacade(TeamService service) {
        this.service = service;
    }

    /** Participa da equipe, como membro ou como lider. */
    public boolean isMember(Long teamId, Long userId) {
        return service.isMember(teamId, userId);
    }

    /** Lidera a equipe. Todo lider tambem e membro. */
    public boolean isLead(Long teamId, Long userId) {
        return service.isLead(teamId, userId);
    }
}
