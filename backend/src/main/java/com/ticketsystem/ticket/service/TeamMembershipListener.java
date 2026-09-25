package com.ticketsystem.ticket.service;

import com.ticketsystem.team.TeamMembershipRemoved;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reage a alguem sair de uma equipe: os tickets dela deixam de te-lo como responsavel atual.
 *
 * <p>Evento, e nao chamada de {@code team} para {@code ticket}: {@code team} nao sabe que
 * tickets existem, e e isso que mantem o grafo de modulos aciclico (CLAUDE.md#comunicacao-entre-modulos-evento-nao-chamada).
 *
 * <p>{@code @ApplicationModuleListener} roda depois do commit de quem publicou, em transacao
 * propria. Se falhar, a publicacao fica pendente no registro do Modulith e e reentregue no
 * proximo start. Ate la, o nome da pessoa continua como responsavel atual — mas ela ja nao
 * enxerga o ticket, porque a visibilidade e decidida pelo vinculo, e nao por este campo.
 */
@Component
class TeamMembershipListener {

    private final TicketAssignmentService atribuicoes;

    TeamMembershipListener(TicketAssignmentService atribuicoes) {
        this.atribuicoes = atribuicoes;
    }

    @ApplicationModuleListener
    void aoSairDaEquipe(TeamMembershipRemoved evento) {
        atribuicoes.releaseCurrentAssignee(evento.teamId(), evento.userId(), evento.removedBy());
    }
}
