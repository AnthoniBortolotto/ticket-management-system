package com.ticketsystem.ticket.service;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.team.TeamFacade;
import com.ticketsystem.ticket.TicketAssignment;
import com.ticketsystem.ticket.TicketAssignmentChanged;
import com.ticketsystem.ticket.domain.AssignmentConflictException;
import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.TicketRepository;
import com.ticketsystem.user.UserFacade;
import com.ticketsystem.user.UserRole;
import java.time.Clock;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Com quem o ticket fica: responsavel atual, modo exclusivo, devolucao e transferencia.
 *
 * <p>Cada operacao segue a mesma ordem de recusas, e a ordem importa:
 * <ol>
 *   <li><strong>404</strong> se o ator nao ve o ticket — o mesmo corpo de um inexistente;</li>
 *   <li><strong>403</strong> se ve e nao pode (a regra e do {@link TicketAccessPolicy});</li>
 *   <li><strong>409</strong> se o modo do ticket nao admite a operacao (a regra e do
 *       {@code Ticket});</li>
 *   <li><strong>400</strong> se o destino nao serve — so depois das tres, para quem nao pode
 *       atribuir nao descobrir, pela resposta, quem participa de qual equipe.</li>
 * </ol>
 *
 * <p>Toda mudanca de verdade publica {@link TicketAssignmentChanged} na mesma transacao; um
 * pedido que nao muda nada nao grava nem publica, para a auditoria nao registrar o que nao
 * aconteceu.
 */
@Service
public class TicketAssignmentService {

    private final TicketRepository tickets;
    private final TicketAccessPolicy acesso;
    private final TeamFacade equipes;
    private final UserFacade usuarios;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    TicketAssignmentService(TicketRepository tickets, TicketAccessPolicy acesso, TeamFacade equipes,
            UserFacade usuarios, ApplicationEventPublisher eventos, Clock relogio) {
        this.tickets = tickets;
        this.acesso = acesso;
        this.equipes = equipes;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /** Qualquer um que atende o ticket designa qualquer membro da equipe — inclusive a si. */
    @Transactional
    public Ticket assignCurrent(CurrentUser ator, Long ticketId, Long assigneeId) {
        Ticket ticket = visivel(ator, ticketId);
        exigirAtendimento(ator, ticket);
        Long equipe = equipeAtual(ticket);
        exigirElegivel(equipe, assigneeId);
        return aplicar(ator.id(), ticket, t -> t.assignCurrent(assigneeId));
    }

    @Transactional
    public Ticket clearCurrent(CurrentUser ator, Long ticketId) {
        Ticket ticket = visivel(ator, ticketId);
        exigirAtendimento(ator, ticket);
        return aplicar(ator.id(), ticket, Ticket::clearCurrent);
    }

    /** O lider da equipe, ou admin, entrega o ticket a um membro so. A equipe vira origem. */
    @Transactional
    public Ticket makeExclusive(CurrentUser ator, Long ticketId, Long assigneeId) {
        Ticket ticket = visivel(ator, ticketId);
        exigirDespacho(ator, ticket);
        Long equipe = equipeAtual(ticket);
        exigirElegivel(equipe, assigneeId);
        return aplicar(ator.id(), ticket, t -> t.makeExclusive(assigneeId));
    }

    /** O responsavel exclusivo, o lider da origem ou admin devolvem o ticket a equipe. */
    @Transactional
    public Ticket returnToTeam(CurrentUser ator, Long ticketId) {
        Ticket ticket = visivel(ator, ticketId);
        exigirAtendimento(ator, ticket);
        return aplicar(ator.id(), ticket, Ticket::returnToTeam);
    }

    /** O lider da equipe atual, ou admin, passa o ticket para outra equipe. */
    @Transactional
    public Ticket transfer(CurrentUser ator, Long ticketId, Long teamId) {
        Ticket ticket = visivel(ator, ticketId);
        exigirDespacho(ator, ticket);
        equipeAtual(ticket);
        if (!equipes.exists(teamId)) {
            throw new UnknownTeamException();
        }
        return aplicar(ator.id(), ticket, t -> t.transferTo(teamId));
    }

    /**
     * Consequencia de alguem sair da equipe: os tickets dela deixam de te-lo como responsavel
     * atual. O ticket continua na equipe, sem dono, para outro membro assumir.
     *
     * <p>Sem ator nem regra de acesso: e reacao a um fato ja decidido em {@code team}.
     * {@code removedBy} vai no evento para a auditoria mostrar de quem foi a decisao.
     */
    @Transactional
    public void releaseCurrentAssignee(Long teamId, Long userId, Long removedBy) {
        for (Ticket ticket : tickets.findInTeamWithCurrentAssignee(teamId, userId)) {
            aplicar(removedBy, ticket, Ticket::clearCurrent);
        }
    }

    private Ticket aplicar(Long quem, Ticket ticket, Consumer<Ticket> operacao) {
        TicketAssignment antes = ticket.assignment();
        operacao.accept(ticket);
        TicketAssignment depois = ticket.assignment();
        if (antes.equals(depois)) {
            return ticket;
        }
        Ticket salvo = tickets.save(ticket);
        eventos.publishEvent(new TicketAssignmentChanged(salvo.getId(), antes, depois, quem, relogio.instant()));
        return salvo;
    }

    /**
     * A equipe do ticket, que so existe em modo equipe. Conferido aqui, e nao so no
     * {@code Ticket}, para o 409 sair antes da checagem do destino — que precisa da equipe.
     */
    private static Long equipeAtual(Ticket ticket) {
        if (!ticket.isInTeamMode()) {
            throw AssignmentConflictException.notInTeamMode();
        }
        return ticket.getAssignedTeamId();
    }

    /** Participa da equipe e nao e solicitante. A participacao vem primeiro: quem nao existe nao participa. */
    private void exigirElegivel(Long equipe, Long pessoa) {
        if (!equipes.isMember(equipe, pessoa) || usuarios.findById(pessoa).role() == UserRole.REQUESTER) {
            throw new IneligibleAssigneeException();
        }
    }

    private void exigirAtendimento(CurrentUser ator, Ticket ticket) {
        if (!acesso.canHandle(ator, ticket)) {
            throw TicketActionForbiddenException.assignment();
        }
    }

    private void exigirDespacho(CurrentUser ator, Ticket ticket) {
        if (!acesso.canDispatch(ator, ticket)) {
            throw TicketActionForbiddenException.dispatch();
        }
    }

    private Ticket visivel(CurrentUser ator, Long ticketId) {
        return tickets.findById(ticketId)
                .filter(ticket -> acesso.canView(ator, ticket))
                .orElseThrow(TicketNotFoundException::new);
    }
}
