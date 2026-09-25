package com.ticketsystem.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.support.TicketBuilder;
import com.ticketsystem.ticket.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TicketTest {

    private static final Long SOLICITANTE = 7L;
    private static final Long EQUIPE = 3L;

    @Test
    @DisplayName("ticket novo nasce OPEN, em modo equipe, com cada id no seu lugar")
    void ticketNovoNasceAbertoEmModoEquipe() {
        // Dois Long lado a lado na assinatura: trocar a ordem compila. Este teste usa numeros
        // diferentes para os dois justamente para pegar isso.
        Ticket ticket = abrir("Sem acesso a VPN", "Desde a troca de senha.");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getRequesterId()).isEqualTo(SOLICITANTE);
        assertThat(ticket.getAssignedTeamId()).isEqualTo(EQUIPE);
        assertThat(ticket.isInTeamMode()).isTrue();
        assertThat(ticket.getCurrentAssigneeId()).isNull();
        assertThat(ticket.getExclusiveAssigneeId()).isNull();
        assertThat(ticket.getOriginTeamId()).isNull();
        assertThat(ticket.getCategory()).isEqualTo(TicketCategory.ACCESS);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    @DisplayName("titulo e descricao sao guardados sem os espacos das pontas")
    void textoEhAparado() {
        Ticket ticket = abrir("  Sem acesso  ", "  Detalhe  ");

        assertThat(ticket.getTitle()).isEqualTo("Sem acesso");
        assertThat(ticket.getDescription()).isEqualTo("Detalhe");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("titulo ou descricao em branco sao recusados")
    void textoEmBrancoEhRecusado(String texto) {
        assertThatThrownBy(() -> abrir(texto, "Detalhe")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> abrir("Titulo", texto)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem solicitante, equipe, categoria ou prioridade, o ticket nao nasce")
    void camposObrigatorios() {
        assertThatThrownBy(() -> Ticket.open(null, EQUIPE, "T", "D", TicketCategory.OTHER, TicketPriority.LOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ticket.open(SOLICITANTE, null, "T", "D", TicketCategory.OTHER, TicketPriority.LOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ticket.open(SOLICITANTE, EQUIPE, "T", "D", null, TicketPriority.LOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ticket.open(SOLICITANTE, EQUIPE, "T", "D", TicketCategory.OTHER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("transicao permitida muda o status e devolve o anterior")
    void transicaoPermitida() {
        Ticket ticket = abrir("T", "D");

        TicketStatus anterior = ticket.transitionTo(TicketStatus.IN_PROGRESS);

        assertThat(anterior).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("transicao fora do fluxo e 409, e o status nao muda")
    void transicaoInvalida() {
        Ticket ticket = abrir("T", "D");

        assertThatThrownBy(() -> ticket.transitionTo(TicketStatus.CLOSED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .extracting(e -> ((InvalidStatusTransitionException) e).kind())
                .isEqualTo(ProblemKind.CONFLICT);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    @DisplayName("ticket em modo exclusivo nao esta em modo equipe")
    void modoExclusivoNaoEhModoEquipe() {
        // Ainda nao ha operacao que leve a este modo — ela e da Fase 5. O builder monta o
        // estado direto para a regra de acesso poder ser testada contra ele desde ja.
        Ticket ticket = TicketBuilder.aTicket().inExclusiveMode(9L, EQUIPE).build();

        assertThat(ticket.isInTeamMode()).isFalse();
    }

    @Test
    @DisplayName("toString nao carrega o texto que o solicitante escreveu")
    void toStringSemTextoLivre() {
        // toString de entidade acaba em log e em mensagem de excecao; titulo e descricao sao
        // texto livre de quem abriu o chamado, e podem ter qualquer coisa.
        Ticket ticket = abrir("Senha do banco: 1234", "Minha senha e hunter2");

        assertThat(ticket.toString()).contains("status=OPEN").doesNotContain("1234", "hunter2");
    }

    private static Ticket abrir(String titulo, String descricao) {
        return Ticket.open(SOLICITANTE, EQUIPE, titulo, descricao, TicketCategory.ACCESS, TicketPriority.HIGH);
    }
}
