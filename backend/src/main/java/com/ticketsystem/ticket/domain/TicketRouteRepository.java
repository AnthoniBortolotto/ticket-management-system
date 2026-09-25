package com.ticketsystem.ticket.domain;

import java.util.List;
import java.util.Optional;

/** O roteamento de categoria para equipe. */
public interface TicketRouteRepository {

    Optional<TicketRoute> findByCategory(TicketCategory category);

    /** Todas as rotas, na ordem das categorias no enum. */
    List<TicketRoute> findAll();

    TicketRoute save(TicketRoute route);
}
