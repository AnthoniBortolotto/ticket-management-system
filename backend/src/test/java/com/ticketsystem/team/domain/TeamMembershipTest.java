package com.ticketsystem.team.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamMembershipTest {

    @Test
    @DisplayName("quem entra na equipe entra como membro, nunca como lider")
    void entraComoMembro() {
        // Lideranca amplia a visibilidade — o lider enxerga os tickets que sairam da equipe
        // para o modo exclusivo. Ela e concedida por um passo proprio, nunca de carona na
        // entrada.
        TeamMembership vinculo = TeamMembership.member(1L, 2L);

        assertThat(vinculo.getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(vinculo.isLead()).isFalse();
        assertThat(vinculo.getTeamId()).isEqualTo(1L);
        assertThat(vinculo.getUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("promover e rebaixar mudam o papel do mesmo vinculo")
    void promoverERebaixar() {
        TeamMembership vinculo = TeamMembership.member(1L, 2L);

        vinculo.changeRole(TeamRole.LEAD);
        assertThat(vinculo.isLead()).isTrue();

        vinculo.changeRole(TeamRole.MEMBER);
        assertThat(vinculo.isLead()).isFalse();
    }

    @Test
    @DisplayName("papel nulo e recusado")
    void papelNuloEhRecusado() {
        TeamMembership vinculo = TeamMembership.member(1L, 2L);

        assertThatThrownBy(() -> vinculo.changeRole(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(vinculo.getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    @DisplayName("vinculo sem equipe ou sem usuario e recusado")
    void vinculoSemAsDuasPontasEhRecusado() {
        assertThatThrownBy(() -> TeamMembership.member(null, 2L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TeamMembership.member(1L, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
