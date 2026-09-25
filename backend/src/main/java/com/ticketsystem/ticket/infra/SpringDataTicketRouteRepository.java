package com.ticketsystem.ticket.infra;

import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketRoute;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Detalhe de implementacao. A busca por categoria usa o UNIQUE da V5. */
interface SpringDataTicketRouteRepository extends JpaRepository<TicketRoute, Long> {

    Optional<TicketRoute> findByCategory(TicketCategory category);
}
