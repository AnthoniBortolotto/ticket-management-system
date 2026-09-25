package com.ticketsystem.ticket.infra;

import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.TicketRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * O adaptador JPA da porta de tickets. Roda o {@code TicketRepositoryContractTest} — e um
 * adaptador que o substitua precisa passar pela mesma suite, sem ela ser reescrita.
 *
 * <p>A recusa de versao desatualizada que o contrato exige vem do {@code @Version} da
 * entidade: o Spring Data traduz o {@code OptimisticLockException} do Hibernate para
 * {@code OptimisticLockingFailureException}, que e o tipo que o contrato nomeia.
 */
@Repository
class JpaTicketRepository implements TicketRepository {

    private final SpringDataTicketRepository springData;

    JpaTicketRepository(SpringDataTicketRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<Ticket> findById(Long id) {
        return springData.findById(id);
    }

    @Override
    public Ticket save(Ticket ticket) {
        return springData.save(ticket);
    }
}
