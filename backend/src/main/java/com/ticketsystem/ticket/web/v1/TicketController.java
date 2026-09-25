package com.ticketsystem.ticket.web.v1;

import com.ticketsystem.auth.AuthFacade;
import com.ticketsystem.ticket.service.TicketService;
import com.ticketsystem.ticket.web.v1.dto.CommentRequest;
import com.ticketsystem.ticket.web.v1.dto.CommentResponse;
import com.ticketsystem.ticket.web.v1.dto.OpenTicketRequest;
import com.ticketsystem.ticket.web.v1.dto.TicketResponse;
import com.ticketsystem.ticket.web.v1.dto.TransitionRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Abrir, consultar, mover e conversar sobre um ticket.
 *
 * <p>Como no {@code TeamController}, nao ha regra por URL alem de exigir autenticacao: quem
 * pode o que depende do ticket — de quem o abriu e de qual equipe o tem —, e a regra mora
 * inteira no {@code TicketAccessPolicy}.
 *
 * <p><strong>Nao ha listagem.</strong> Ela chega na Fase 5, com o filtro de visibilidade na
 * query, escrito teste primeiro contra Postgres. Uma listagem sem esse filtro seria o
 * vazamento que o sistema existe para impedir.
 */
@RestController
@RequestMapping("/api/v1/tickets")
class TicketController {

    private final TicketService service;
    private final AuthFacade auth;

    TicketController(TicketService service, AuthFacade auth) {
        this.service = service;
        this.auth = auth;
    }

    /** A equipe nao vem no pedido: sai da rota da categoria. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TicketResponse open(@Valid @RequestBody OpenTicketRequest pedido) {
        return TicketResponse.from(service.open(
                auth.currentUser(), pedido.title(), pedido.description(), pedido.category(), pedido.priority()));
    }

    @GetMapping("/{ticketId}")
    TicketResponse findById(@PathVariable Long ticketId) {
        return TicketResponse.from(service.findById(auth.currentUser(), ticketId));
    }

    /**
     * Uma transicao e uma acao, e nao um campo editavel: {@code POST} num sub-recurso, e nao
     * {@code PATCH} no ticket. Repetir o pedido nao e neutro — a segunda vez e 409, porque o
     * ticket ja nao esta no status de origem.
     */
    @PostMapping("/{ticketId}/transitions")
    TicketResponse transition(@PathVariable Long ticketId, @Valid @RequestBody TransitionRequest pedido) {
        return TicketResponse.from(service.transition(auth.currentUser(), ticketId, pedido.to()));
    }

    /** Quem nao atende o ticket recebe a conversa sem as notas internas. */
    @GetMapping("/{ticketId}/comments")
    List<CommentResponse> comments(@PathVariable Long ticketId) {
        return service.comments(auth.currentUser(), ticketId).stream().map(CommentResponse::from).toList();
    }

    @PostMapping("/{ticketId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse comment(@PathVariable Long ticketId, @Valid @RequestBody CommentRequest pedido) {
        return CommentResponse.from(service.comment(auth.currentUser(), ticketId, pedido.body(), pedido.internal()));
    }
}
