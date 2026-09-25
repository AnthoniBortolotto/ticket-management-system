package com.ticketsystem.ticket.infra;

import com.ticketsystem.ticket.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Detalhe de implementacao: o service nao a enxerga.
 *
 * <p>Sem {@code JpaSpecificationExecutor} ainda: ele chega na Fase 5 junto com
 * {@code TicketSpecifications}, porque o unico uso dele e a listagem filtrada pela
 * visibilidade — e listagem sem esse filtro nao pode existir.
 */
interface SpringDataTicketRepository extends JpaRepository<Ticket, Long> {
}
