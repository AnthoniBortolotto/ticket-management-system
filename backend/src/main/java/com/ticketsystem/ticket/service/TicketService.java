package com.ticketsystem.ticket.service;

import com.ticketsystem.auth.CurrentUser;
import com.ticketsystem.team.TeamFacade;
import com.ticketsystem.team.UserTeams;
import com.ticketsystem.ticket.TicketStatus;
import com.ticketsystem.ticket.TicketStatusChanged;
import com.ticketsystem.ticket.domain.Comment;
import com.ticketsystem.ticket.domain.CommentRepository;
import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketPage;
import com.ticketsystem.ticket.domain.TicketPriority;
import com.ticketsystem.ticket.domain.TicketRepository;
import com.ticketsystem.ticket.domain.TicketRouteRepository;
import com.ticketsystem.ticket.domain.VisibilityScope;
import com.ticketsystem.user.UserRole;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abrir, consultar, mover e conversar sobre um ticket.
 *
 * <p>Toda operacao sobre um ticket existente comeca pela mesma pergunta — o ator o enxerga?
 * — e quem nao enxerga recebe 404, o mesmo corpo de um ticket que nao existe. So depois vem
 * a permissao para a operacao (403) e a regra de fluxo (409). As tres decisoes moram em
 * lugares diferentes de proposito: visibilidade e permissao no {@link TicketAccessPolicy},
 * fluxo no {@code TicketStatus}.
 *
 * <p>O ator chega como parametro, como no {@code TeamService}: o service nao le o contexto
 * de seguranca.
 */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final CommentRepository comentarios;
    private final TicketRouteRepository rotas;
    private final TicketAccessPolicy acesso;
    private final TeamFacade equipes;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    TicketService(TicketRepository tickets, CommentRepository comentarios, TicketRouteRepository rotas,
            TicketAccessPolicy acesso, TeamFacade equipes, ApplicationEventPublisher eventos, Clock relogio) {
        this.tickets = tickets;
        this.comentarios = comentarios;
        this.rotas = rotas;
        this.acesso = acesso;
        this.equipes = equipes;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * Abre um chamado em nome de quem pede, na equipe que a categoria indica.
     *
     * <p>Qualquer pessoa autenticada abre — inclusive agente e admin, que tambem tem problemas.
     * Categoria sem rota e 409: nao ha equipe default, porque um default esconderia a
     * configuracao que falta.
     */
    @Transactional
    public Ticket open(CurrentUser ator, String title, String description, TicketCategory category,
            TicketPriority priority) {
        Long equipe = rotas.findByCategory(category)
                .orElseThrow(UnroutedCategoryException::new)
                .getTeamId();
        return tickets.save(Ticket.open(ator.id(), equipe, title, description, category, priority));
    }

    /**
     * Os tickets que o ator enxerga, filtrados no banco.
     *
     * <p>As equipes vem da {@code TeamFacade} a cada chamada, e nao do token: sair de uma
     * equipe vale na proxima listagem. O escopo espelha o {@link TicketAccessPolicy} — admin
     * ve tudo, papel {@code REQUESTER} so os proprios, e os demais tambem os das suas equipes.
     */
    @Transactional(readOnly = true)
    public TicketPage list(CurrentUser ator, int page, int size) {
        return tickets.findVisible(escopoDe(ator), page, size);
    }

    @Transactional(readOnly = true)
    public Ticket findById(CurrentUser ator, Long ticketId) {
        return visivel(ator, ticketId);
    }

    /**
     * Move o ticket e publica {@link TicketStatusChanged} na mesma transacao.
     *
     * <p>Se a gravacao falhar — inclusive por outra transicao concorrente, pego pela versao —,
     * a publicacao volta junto: nenhum listener recebe uma mudanca que nao aconteceu.
     */
    @Transactional
    public Ticket transition(CurrentUser ator, Long ticketId, TicketStatus destino) {
        Ticket ticket = visivel(ator, ticketId);
        if (!acesso.canTransition(ator, ticket, destino)) {
            throw TicketActionForbiddenException.transition();
        }
        TicketStatus origem = ticket.transitionTo(destino);
        Ticket salvo = tickets.save(ticket);
        eventos.publishEvent(new TicketStatusChanged(salvo.getId(), origem, destino, ator.id(), relogio.instant()));
        return salvo;
    }

    /** Resposta publica, de quem enxerga o ticket; nota interna, so de quem o atende. */
    @Transactional
    public Comment comment(CurrentUser ator, Long ticketId, String body, boolean internal) {
        Ticket ticket = visivel(ator, ticketId);
        if (internal && !acesso.canHandle(ator, ticket)) {
            throw TicketActionForbiddenException.internalNote();
        }
        Comment novo = internal
                ? Comment.internalNote(ticket.getId(), ator.id(), body)
                : Comment.publicReply(ticket.getId(), ator.id(), body);
        return comentarios.save(novo);
    }

    /**
     * A conversa, do jeito que o ator pode le-la.
     *
     * <p>Quem nao atende recebe a consulta que ja exclui as notas internas no banco — e nao a
     * conversa inteira filtrada aqui. E a regra do CLAUDE.md para listagem, aplicada a
     * conversa.
     */
    @Transactional(readOnly = true)
    public List<Comment> comments(CurrentUser ator, Long ticketId) {
        Ticket ticket = visivel(ator, ticketId);
        return acesso.canHandle(ator, ticket)
                ? comentarios.findByTicket(ticket.getId())
                : comentarios.findPublicByTicket(ticket.getId());
    }

    private VisibilityScope escopoDe(CurrentUser ator) {
        if (ator.isAdmin()) {
            return VisibilityScope.everything(ator.id());
        }
        if (ator.role() == UserRole.REQUESTER) {
            return VisibilityScope.ownTicketsOnly(ator.id());
        }
        UserTeams dele = equipes.teamsOf(ator.id());
        return VisibilityScope.agent(ator.id(), dele.memberOf(), dele.leads());
    }

    private Ticket visivel(CurrentUser ator, Long ticketId) {
        return tickets.findById(ticketId)
                .filter(ticket -> acesso.canView(ator, ticket))
                .orElseThrow(TicketNotFoundException::new);
    }
}
