package com.ticketsystem.ticket.service;

import static org.mockito.Mockito.verify;

import com.ticketsystem.team.TeamMembershipRemoved;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamMembershipListenerTest {

    @Mock
    private TicketAssignmentService atribuicoes;

    @InjectMocks
    private TeamMembershipListener listener;

    @Test
    @DisplayName("repassa equipe, pessoa e quem removeu, cada um no seu lugar")
    void repassaNaOrdemCerta() {
        // Tres Long seguidos: uma troca de ordem compila e limpa o responsavel da equipe errada.
        // Numeros diferentes para cada um sao o que torna a troca visivel aqui.
        listener.aoSairDaEquipe(new TeamMembershipRemoved(3L, 20L, 30L, Instant.parse("2026-09-25T12:00:00Z")));

        verify(atribuicoes).releaseCurrentAssignee(3L, 20L, 30L);
    }
}
