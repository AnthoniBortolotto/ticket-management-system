package com.ticketsystem.ticket.infra;

import com.ticketsystem.ticket.domain.Ticket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Detalhe de implementacao: o service nao a enxerga.
 *
 * <p>Sem {@code JpaSpecificationExecutor}: a listagem filtrada e montada no
 * {@code JpaTicketRepository}, porque a ordem por prioridade nao cabe num {@code Sort}.
 */
interface SpringDataTicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByAssignedTeamIdAndCurrentAssigneeId(Long assignedTeamId, Long currentAssigneeId);
}
