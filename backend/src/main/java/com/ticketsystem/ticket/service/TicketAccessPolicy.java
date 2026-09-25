package com.ticketsystem.ticket.service;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.team.TeamFacade;
import com.ticketsystem.ticket.TicketStatus;
import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.user.UserRole;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Quem pode ver, atender e mover um ticket <strong>ja carregado</strong>.
 *
 * <p>E metade da regra de visibilidade (CLAUDE.md#visibilidade). A outra metade, a mesma
 * regra em SQL para a listagem, chega na Fase 5 em {@code TicketSpecifications} — e as duas
 * precisam concordar, linha a linha. <strong>Mudou aqui, revise la.</strong>
 *
 * <p>O que vale nesta fase:
 * <ul>
 *   <li><em>Admin</em> ve e atende tudo.</li>
 *   <li><em>Solicitante</em> sempre ve o proprio ticket, em qualquer modo, e so decide duas
 *       coisas: confirmar o fechamento e reabrir. Nunca atende — logo, nunca le nota
 *       interna.</li>
 *   <li><em>Membro da equipe</em> ve e atende os tickets em modo equipe dela.</li>
 * </ul>
 *
 * <p><strong>Modo exclusivo falha fechado.</strong> A Fase 5 acrescenta o responsavel
 * exclusivo e o lider da equipe de origem. Ate la, um ticket nesse modo — que so um script
 * ou um adaptador futuro criaria — e visivel apenas para admin e solicitante. Faltar uma
 * regra aqui esconde um ticket; sobrar uma regra o vaza.
 *
 * <p>Papel {@code REQUESTER} nunca atende, mesmo com vinculo de equipe: a Fase 3 impede o
 * vinculo, mas um agente rebaixado a solicitante manteria o antigo, e passaria a ler notas
 * internas.
 */
@Component
public class TicketAccessPolicy {

    /** As transicoes que sao decisao do solicitante. Se o fluxo as permite, e o TicketStatus que diz. */
    private static final Set<TicketStatus> DECISOES_DO_SOLICITANTE = Set.of(TicketStatus.CLOSED, TicketStatus.REOPENED);

    private final TeamFacade equipes;

    TicketAccessPolicy(TeamFacade equipes) {
        this.equipes = equipes;
    }

    public boolean canView(CurrentUser ator, Ticket ticket) {
        return isRequester(ator, ticket) || canHandle(ator, ticket);
    }

    /** Atende: le e escreve nota interna e move o ticket por qualquer transicao do fluxo. */
    public boolean canHandle(CurrentUser ator, Ticket ticket) {
        if (ator.isAdmin()) {
            return true;
        }
        if (ator.role() == UserRole.REQUESTER || !ticket.isInTeamMode()) {
            return false;
        }
        return equipes.isMember(ticket.getAssignedTeamId(), ator.id());
    }

    public boolean canTransition(CurrentUser ator, Ticket ticket, TicketStatus destino) {
        return canHandle(ator, ticket)
                || (isRequester(ator, ticket) && DECISOES_DO_SOLICITANTE.contains(destino));
    }

    private static boolean isRequester(CurrentUser ator, Ticket ticket) {
        return ticket.getRequesterId().equals(ator.id());
    }
}
