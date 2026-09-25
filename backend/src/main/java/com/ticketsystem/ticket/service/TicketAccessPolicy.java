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
 * regra em SQL para a listagem, e o {@code TicketSpecifications} — e as duas precisam
 * concordar, linha a linha; o {@code TicketSpecificationsIT} compara as duas caso a caso.
 * <strong>Mudou aqui, mude la.</strong>
 *
 * <ul>
 *   <li><em>Admin</em> ve, atende e despacha tudo.</li>
 *   <li><em>Solicitante</em> sempre ve o proprio ticket, em qualquer modo, e so decide duas
 *       coisas: confirmar o fechamento e reabrir. Nunca atende — logo, nunca le nota
 *       interna.</li>
 *   <li><em>Modo equipe:</em> membro da equipe ve e atende; o lider dela tambem despacha —
 *       manda para o exclusivo ou para outra equipe.</li>
 *   <li><em>Modo exclusivo:</em> a equipe perde o acesso. Veem e atuam o responsavel exclusivo
 *       e o lider da equipe de origem. Nenhum dos dois despacha: para mudar o ticket de lugar,
 *       ele volta para a equipe antes.</li>
 * </ul>
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

    /**
     * Atende: le e escreve nota interna, move o ticket por qualquer transicao do fluxo, cuida
     * do responsavel atual e, no modo exclusivo, devolve o ticket a equipe.
     */
    public boolean canHandle(CurrentUser ator, Ticket ticket) {
        if (ator.isAdmin()) {
            return true;
        }
        if (ator.role() == UserRole.REQUESTER) {
            return false;
        }
        if (ticket.isInTeamMode()) {
            return equipes.isMember(ticket.getAssignedTeamId(), ator.id());
        }
        return ator.id().equals(ticket.getExclusiveAssigneeId()) || equipes.isLead(ticket.getOriginTeamId(), ator.id());
    }

    /**
     * Decide para onde o ticket vai: modo exclusivo ou outra equipe. Admin, ou o lider da
     * equipe em que o ticket esta. Em modo exclusivo ninguem alem do admin despacha — o
     * ticket precisa voltar a equipe antes.
     */
    public boolean canDispatch(CurrentUser ator, Ticket ticket) {
        if (ator.isAdmin()) {
            return true;
        }
        return ator.role() != UserRole.REQUESTER
                && ticket.isInTeamMode()
                && equipes.isLead(ticket.getAssignedTeamId(), ator.id());
    }

    public boolean canTransition(CurrentUser ator, Ticket ticket, TicketStatus destino) {
        return canHandle(ator, ticket)
                || (isRequester(ator, ticket) && DECISOES_DO_SOLICITANTE.contains(destino));
    }

    private static boolean isRequester(CurrentUser ator, Ticket ticket) {
        return ticket.getRequesterId().equals(ator.id());
    }
}
