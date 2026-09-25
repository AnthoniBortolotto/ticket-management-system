package com.ticketsystem.ticket.service;

import com.ticketsystem.team.TeamFacade;
import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketRoute;
import com.ticketsystem.ticket.domain.TicketRouteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configura para qual equipe vai cada categoria.
 *
 * <p>Nao recebe ator: a regra e so de papel — admin — e mora no {@code SecurityConfig}, por
 * URL, como a de {@code /api/v1/users}. E o contrario do {@code TicketService}, cuja regra
 * depende do ticket e por isso nao cabe numa URL.
 */
@Service
public class TicketRouteService {

    private final TicketRouteRepository rotas;
    private final TeamFacade equipes;

    TicketRouteService(TicketRouteRepository rotas, TeamFacade equipes) {
        this.rotas = rotas;
        this.equipes = equipes;
    }

    @Transactional(readOnly = true)
    public List<TicketRoute> findAll() {
        return rotas.findAll();
    }

    /**
     * Cria a rota da categoria, ou redireciona a que existe. Vale para os proximos tickets;
     * os ja abertos continuam na equipe em que estao.
     */
    @Transactional
    public TicketRoute route(TicketCategory category, Long teamId) {
        if (!equipes.exists(teamId)) {
            throw new RouteTeamNotFoundException();
        }
        TicketRoute rota = rotas.findByCategory(category)
                .map(existente -> {
                    existente.redirectTo(teamId);
                    return existente;
                })
                .orElseGet(() -> new TicketRoute(category, teamId));
        return rotas.save(rota);
    }
}
