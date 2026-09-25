package com.ticketsystem.ticket.web.v1;

import com.ticketsystem.auth.AuthFacade;
import com.ticketsystem.common.web.PageResponse;
import com.ticketsystem.ticket.domain.TicketPage;
import com.ticketsystem.ticket.service.TicketService;
import com.ticketsystem.ticket.web.v1.dto.CommentRequest;
import com.ticketsystem.ticket.web.v1.dto.CommentResponse;
import com.ticketsystem.ticket.web.v1.dto.OpenTicketRequest;
import com.ticketsystem.ticket.web.v1.dto.TicketResponse;
import com.ticketsystem.ticket.web.v1.dto.TransitionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Abrir, consultar, mover e conversar sobre um ticket.
 *
 * <p>Como no {@code TeamController}, nao ha regra por URL alem de exigir autenticacao: quem
 * pode o que depende do ticket — de quem o abriu e de qual equipe o tem —, e a regra mora
 * inteira no {@code TicketAccessPolicy}.
 *
 * <p>A listagem filtra no banco pela mesma regra do ticket carregado — o
 * {@code TicketSpecificationsIT} confere que as duas concordam.
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

    /**
     * Os tickets que quem pede enxerga, URGENT primeiro e, entre iguais, do mais antigo.
     *
     * <p>Pagina a partir de zero, 20 por padrao e no maximo 100: sem teto, um {@code size}
     * enorme carregaria a base inteira numa requisicao.
     */
    @GetMapping
    PageResponse<TicketResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        TicketPage pagina = service.list(auth.currentUser(), page, size);
        return PageResponse.of(pagina.content(), TicketResponse::from, pagina.page(), pagina.size(), pagina.totalElements());
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
