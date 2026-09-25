package com.ticketsystem.ticket.web.v1;

import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.service.TicketRouteService;
import com.ticketsystem.ticket.web.v1.dto.RouteRequest;
import com.ticketsystem.ticket.web.v1.dto.RouteResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Para qual equipe vai cada categoria. So admin.
 *
 * <p>A restricao mora no {@code SecurityConfig}, por URL: e regra de papel, sem estado
 * nenhum, como a de {@code /api/v1/users}.
 */
@RestController
@RequestMapping("/api/v1/ticket-routes")
class TicketRouteController {

    private final TicketRouteService service;

    TicketRouteController(TicketRouteService service) {
        this.service = service;
    }

    /** As rotas configuradas. Categoria ausente daqui recusa a abertura de ticket. */
    @GetMapping
    List<RouteResponse> findAll() {
        return service.findAll().stream().map(RouteResponse::from).toList();
    }

    /** Cria ou redireciona. Idempotente: a categoria e a chave, e repetir nao cria outra rota. */
    @PutMapping("/{category}")
    RouteResponse route(@PathVariable TicketCategory category, @Valid @RequestBody RouteRequest pedido) {
        return RouteResponse.from(service.route(category, pedido.teamId()));
    }
}
