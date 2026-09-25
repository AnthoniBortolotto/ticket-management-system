package com.ticketsystem.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketRouteTest {

    @Test
    @DisplayName("a rota leva a categoria a uma equipe, e pode ser redirecionada")
    void rotaERedirecionamento() {
        TicketRoute rota = new TicketRoute(TicketCategory.ACCESS, 3L);
        assertThat(rota.getCategory()).isEqualTo(TicketCategory.ACCESS);
        assertThat(rota.getTeamId()).isEqualTo(3L);

        rota.redirectTo(4L);

        assertThat(rota.getTeamId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("rota sem categoria ou sem equipe e recusada")
    void camposObrigatorios() {
        assertThatThrownBy(() -> new TicketRoute(null, 3L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TicketRoute(TicketCategory.ACCESS, null)).isInstanceOf(IllegalArgumentException.class);
        TicketRoute rota = new TicketRoute(TicketCategory.ACCESS, 3L);
        assertThatThrownBy(() -> rota.redirectTo(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
