package com.ticketsystem.ticket.infra;

import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketRoute;
import com.ticketsystem.ticket.domain.TicketRouteRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Adapta o Spring Data a porta de rotas.
 *
 * <p>A ordem do enum e aplicada aqui, e nao no SQL: a coluna guarda o nome, e {@code ORDER BY}
 * ordenaria alfabeticamente. Sao no maximo uma linha por categoria — ordenar em memoria nao
 * custa nada, e nao ha filtro de acesso em jogo.
 */
@Repository
class JpaTicketRouteRepository implements TicketRouteRepository {

    private final SpringDataTicketRouteRepository springData;

    JpaTicketRouteRepository(SpringDataTicketRouteRepository springData) {
        this.springData = springData;
    }

    @Override
    public Optional<TicketRoute> findByCategory(TicketCategory category) {
        return springData.findByCategory(category);
    }

    @Override
    public List<TicketRoute> findAll() {
        return springData.findAll().stream()
                .sorted(Comparator.comparing(TicketRoute::getCategory))
                .toList();
    }

    @Override
    public TicketRoute save(TicketRoute route) {
        return springData.save(route);
    }
}
