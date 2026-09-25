package com.ticketsystem.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.team.TeamFacade;
import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketRoute;
import com.ticketsystem.ticket.domain.TicketRouteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketRouteServiceTest {

    @Mock
    private TicketRouteRepository rotas;

    @Mock
    private TeamFacade equipes;

    @InjectMocks
    private TicketRouteService service;

    @Test
    @DisplayName("categoria sem rota ganha uma")
    void criaRota() {
        when(equipes.exists(3L)).thenReturn(true);
        when(rotas.findByCategory(TicketCategory.ACCESS)).thenReturn(Optional.empty());
        when(rotas.save(any(TicketRoute.class))).thenAnswer(chamada -> chamada.getArgument(0));

        TicketRoute rota = service.route(TicketCategory.ACCESS, 3L);

        assertThat(rota.getCategory()).isEqualTo(TicketCategory.ACCESS);
        assertThat(rota.getTeamId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("categoria com rota e redirecionada, sem criar uma segunda")
    void redirecionaRota() {
        TicketRoute existente = new TicketRoute(TicketCategory.ACCESS, 3L);
        when(equipes.exists(4L)).thenReturn(true);
        when(rotas.findByCategory(TicketCategory.ACCESS)).thenReturn(Optional.of(existente));
        when(rotas.save(existente)).thenReturn(existente);

        assertThat(service.route(TicketCategory.ACCESS, 4L).getTeamId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("equipe que nao existe e 400, e nada e gravado")
    void equipeInexistenteEh400() {
        when(equipes.exists(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.route(TicketCategory.ACCESS, 99L))
                .isInstanceOf(RouteTeamNotFoundException.class)
                .extracting(e -> ((RouteTeamNotFoundException) e).kind())
                .isEqualTo(ProblemKind.INVALID);
        verify(rotas, never()).save(any());
    }

    @Test
    @DisplayName("lista as rotas configuradas")
    void listaRotas() {
        List<TicketRoute> todas = List.of(new TicketRoute(TicketCategory.OTHER, 3L));
        when(rotas.findAll()).thenReturn(todas);

        assertThat(service.findAll()).isEqualTo(todas);
    }
}
