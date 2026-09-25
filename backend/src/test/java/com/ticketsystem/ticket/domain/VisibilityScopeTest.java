package com.ticketsystem.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * As tres formas de escopo, campo a campo. Os testes do service comparam escopos montados por
 * estas mesmas fabricas dos dois lados — entao so aqui um sinal trocado dentro delas aparece.
 */
class VisibilityScopeTest {

    @Test
    @DisplayName("admin: sem restricao")
    void admin() {
        assertThat(VisibilityScope.everything(1L))
                .isEqualTo(new VisibilityScope(1L, true, true, Set.of(), Set.of()));
    }

    @Test
    @DisplayName("solicitante: com restricao, sem atender e sem equipe nenhuma")
    void solicitante() {
        assertThat(VisibilityScope.ownTicketsOnly(7L))
                .isEqualTo(new VisibilityScope(7L, false, false, Set.of(), Set.of()));
    }

    @Test
    @DisplayName("agente: com restricao, atende, e leva as equipes como vieram")
    void agente() {
        assertThat(VisibilityScope.agent(9L, Set.of(3L, 4L), Set.of(4L)))
                .isEqualTo(new VisibilityScope(9L, false, true, Set.of(3L, 4L), Set.of(4L)));
    }
}
