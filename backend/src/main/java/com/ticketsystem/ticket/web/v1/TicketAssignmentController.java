package com.ticketsystem.ticket.web.v1;

import com.ticketsystem.auth.AuthFacade;
import com.ticketsystem.ticket.service.TicketAssignmentService;
import com.ticketsystem.ticket.web.v1.dto.AssigneeRequest;
import com.ticketsystem.ticket.web.v1.dto.TicketResponse;
import com.ticketsystem.ticket.web.v1.dto.TransferRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Com quem o ticket esta.
 *
 * <p>O responsavel atual e um campo — {@code PUT} e {@code DELETE}, idempotentes. Mandar para
 * o exclusivo, devolver e transferir sao decisoes que tiram o ticket de um lugar e o poem em
 * outro — {@code POST} de acao, como a transicao de status. Toda resposta devolve o ticket
 * como ficou.
 *
 * <p>Sem regra no {@code SecurityConfig}: quem pode depende do ticket e das equipes, e mora no
 * {@code TicketAccessPolicy}.
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/assignment")
class TicketAssignmentController {

    private final TicketAssignmentService service;
    private final AuthFacade auth;

    TicketAssignmentController(TicketAssignmentService service, AuthFacade auth) {
        this.service = service;
        this.auth = auth;
    }

    /** Qualquer um que atende o ticket designa qualquer membro da equipe dele. */
    @PutMapping("/current")
    TicketResponse assignCurrent(@PathVariable Long ticketId, @Valid @RequestBody AssigneeRequest pedido) {
        return TicketResponse.from(service.assignCurrent(auth.currentUser(), ticketId, pedido.assigneeId()));
    }

    @DeleteMapping("/current")
    TicketResponse clearCurrent(@PathVariable Long ticketId) {
        return TicketResponse.from(service.clearCurrent(auth.currentUser(), ticketId));
    }

    /** Lider da equipe ou admin; o destino precisa participar da equipe. */
    @PostMapping("/exclusive")
    TicketResponse makeExclusive(@PathVariable Long ticketId, @Valid @RequestBody AssigneeRequest pedido) {
        return TicketResponse.from(service.makeExclusive(auth.currentUser(), ticketId, pedido.assigneeId()));
    }

    /** O responsavel exclusivo, o lider da equipe de origem ou admin. */
    @PostMapping("/return")
    TicketResponse returnToTeam(@PathVariable Long ticketId) {
        return TicketResponse.from(service.returnToTeam(auth.currentUser(), ticketId));
    }

    /** Lider da equipe atual ou admin. O responsavel atual nao vai junto. */
    @PostMapping("/transfer")
    TicketResponse transfer(@PathVariable Long ticketId, @Valid @RequestBody TransferRequest pedido) {
        return TicketResponse.from(service.transfer(auth.currentUser(), ticketId, pedido.teamId()));
    }
}
