package com.ticketsystem.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.support.TicketBuilder;
import com.ticketsystem.ticket.TicketAssignment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * As operacoes de atribuicao do ticket, e o invariante que elas nunca podem quebrar: o ticket
 * esta sempre em exatamente um modo, com a origem preenchida so no exclusivo e o responsavel
 * atual so no modo equipe. E a mesma regra dos CHECKs da V5, aqui do lado do Java.
 */
class TicketAssignmentTest {

    private static final Long EQUIPE = 3L;
    private static final Long OUTRA_EQUIPE = 4L;
    private static final Long DIEGO = 20L;
    private static final Long MARINA = 21L;

    @Nested
    @DisplayName("responsavel atual")
    class ResponsavelAtual {

        @Test
        @DisplayName("em modo equipe, alguem assume, outro toma o lugar, e o ticket continua da equipe")
        void assumirETrocar() {
            Ticket ticket = emModoEquipe();

            ticket.assignCurrent(DIEGO);
            ticket.assignCurrent(MARINA);

            assertThat(ticket.assignment()).isEqualTo(new TicketAssignment(EQUIPE, MARINA, null, null));
        }

        @Test
        @DisplayName("largar o ticket deixa a equipe sem responsavel, e continua da equipe")
        void largar() {
            Ticket ticket = emModoEquipe();
            ticket.assignCurrent(DIEGO);

            ticket.clearCurrent();

            assertThat(ticket.assignment()).isEqualTo(new TicketAssignment(EQUIPE, null, null, null));
        }

        @Test
        @DisplayName("em modo exclusivo nao ha responsavel atual: 409")
        void emModoExclusivoEh409() {
            Ticket ticket = emModoExclusivo();

            assertThatThrownBy(() -> ticket.assignCurrent(MARINA)).isInstanceOf(AssignmentConflictException.class)
                    .extracting(e -> ((AssignmentConflictException) e).kind()).isEqualTo(ProblemKind.CONFLICT);
            assertThatThrownBy(ticket::clearCurrent).isInstanceOf(AssignmentConflictException.class);
            assertThat(ticket.assignment()).isEqualTo(new TicketAssignment(null, null, DIEGO, EQUIPE));
        }

        @Test
        @DisplayName("responsavel nulo e recusado — largar e outra operacao")
        void nuloEhRecusado() {
            assertThatThrownBy(() -> emModoEquipe().assignCurrent(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("modo exclusivo")
    class ModoExclusivo {

        @Test
        @DisplayName("ir para exclusivo guarda a equipe de origem e tira a equipe e o responsavel atual")
        void irParaExclusivo() {
            Ticket ticket = emModoEquipe();
            ticket.assignCurrent(MARINA);

            ticket.makeExclusive(DIEGO);

            assertThat(ticket.assignment()).isEqualTo(new TicketAssignment(null, null, DIEGO, EQUIPE));
            assertThat(ticket.isInTeamMode()).isFalse();
        }

        @Test
        @DisplayName("ja exclusivo nao vai para exclusivo de novo: 409")
        void jaExclusivoEh409() {
            // Trocar o responsavel exclusivo e devolver e mandar de novo: duas decisoes, dois
            // eventos, e nenhuma troca silenciosa de quem pode ver o ticket.
            Ticket ticket = emModoExclusivo();

            assertThatThrownBy(() -> ticket.makeExclusive(MARINA)).isInstanceOf(AssignmentConflictException.class);
            assertThat(ticket.getExclusiveAssigneeId()).isEqualTo(DIEGO);
        }

        @Test
        @DisplayName("devolver volta para a equipe de origem, sem responsavel, e apaga a origem")
        void devolver() {
            Ticket ticket = emModoExclusivo();

            ticket.returnToTeam();

            assertThat(ticket.assignment()).isEqualTo(new TicketAssignment(EQUIPE, null, null, null));
        }

        @Test
        @DisplayName("devolver o que ja esta em modo equipe: 409")
        void devolverEmModoEquipeEh409() {
            assertThatThrownBy(() -> emModoEquipe().returnToTeam()).isInstanceOf(AssignmentConflictException.class);
        }

        @Test
        @DisplayName("responsavel exclusivo nulo e recusado")
        void nuloEhRecusado() {
            assertThatThrownBy(() -> emModoEquipe().makeExclusive(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("transferencia")
    class Transferencia {

        @Test
        @DisplayName("transferir troca a equipe e limpa o responsavel, que nao e da equipe nova")
        void transferir() {
            Ticket ticket = emModoEquipe();
            ticket.assignCurrent(DIEGO);

            ticket.transferTo(OUTRA_EQUIPE);

            assertThat(ticket.assignment()).isEqualTo(new TicketAssignment(OUTRA_EQUIPE, null, null, null));
        }

        @Test
        @DisplayName("transferir para a mesma equipe: 409")
        void mesmaEquipeEh409() {
            Ticket ticket = emModoEquipe();
            ticket.assignCurrent(DIEGO);

            assertThatThrownBy(() -> ticket.transferTo(EQUIPE)).isInstanceOf(AssignmentConflictException.class);
            assertThat(ticket.getCurrentAssigneeId()).isEqualTo(DIEGO);
        }

        @Test
        @DisplayName("ticket exclusivo nao se transfere: devolve antes")
        void exclusivoNaoTransfere() {
            assertThatThrownBy(() -> emModoExclusivo().transferTo(OUTRA_EQUIPE))
                    .isInstanceOf(AssignmentConflictException.class);
        }

        @Test
        @DisplayName("equipe nula e recusada")
        void nulaEhRecusada() {
            assertThatThrownBy(() -> emModoEquipe().transferTo(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static Ticket emModoEquipe() {
        return TicketBuilder.aTicket().assignedToTeam(EQUIPE).build();
    }

    private static Ticket emModoExclusivo() {
        return TicketBuilder.aTicket().inExclusiveMode(DIEGO, EQUIPE).build();
    }
}
