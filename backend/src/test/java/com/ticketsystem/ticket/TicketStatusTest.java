package com.ticketsystem.ticket;

import static com.ticketsystem.ticket.TicketStatus.CLOSED;
import static com.ticketsystem.ticket.TicketStatus.IN_PROGRESS;
import static com.ticketsystem.ticket.TicketStatus.OPEN;
import static com.ticketsystem.ticket.TicketStatus.REOPENED;
import static com.ticketsystem.ticket.TicketStatus.RESOLVED;
import static com.ticketsystem.ticket.TicketStatus.WAITING_CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * O fluxo de status, conferido par a par.
 *
 * <p>A tabela abaixo e o requisito, copiado do README e da decisao da Fase 4 — e nao do
 * codigo. O teste percorre <strong>todos</strong> os 36 pares, e nao so os permitidos: uma
 * transicao liberada por engano e o erro que um teste de "caminho feliz" nunca pega.
 */
class TicketStatusTest {

    private static final Map<TicketStatus, Set<TicketStatus>> PERMITIDAS = Map.of(
            OPEN, EnumSet.of(IN_PROGRESS),
            // Resolver direto, sem esperar o cliente, e o atalho decidido na Fase 4.
            IN_PROGRESS, EnumSet.of(WAITING_CUSTOMER, RESOLVED),
            WAITING_CUSTOMER, EnumSet.of(IN_PROGRESS, RESOLVED),
            RESOLVED, EnumSet.of(CLOSED, REOPENED),
            CLOSED, EnumSet.of(REOPENED),
            REOPENED, EnumSet.of(IN_PROGRESS));

    static Stream<Arguments> todosOsPares() {
        List<Arguments> pares = new ArrayList<>();
        for (TicketStatus de : TicketStatus.values()) {
            for (TicketStatus para : TicketStatus.values()) {
                pares.add(Arguments.of(de, para, PERMITIDAS.get(de).contains(para)));
            }
        }
        return pares.stream();
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @MethodSource("todosOsPares")
    @DisplayName("cada par de status e permitido ou recusado conforme o fluxo")
    void cadaPar(TicketStatus de, TicketStatus para, boolean permitida) {
        assertThat(de.canTransitionTo(para)).isEqualTo(permitida);
    }

    @Test
    @DisplayName("a tabela do teste cobre todo status do enum")
    void tabelaCobreTodoStatus() {
        // Um status novo sem linha na tabela faria todosOsPares() estourar com
        // NullPointerException — este teste diz o motivo em vez disso.
        assertThat(PERMITIDAS.keySet()).containsExactlyInAnyOrder(TicketStatus.values());
    }

    @Test
    @DisplayName("nenhum status transiciona para si mesmo")
    void semTransicaoParaSiMesmo() {
        // "Mudar" para o mesmo status publicaria um TicketStatusChanged sem mudanca nenhuma,
        // e a auditoria registraria um evento que nao aconteceu.
        for (TicketStatus status : TicketStatus.values()) {
            assertThat(status.canTransitionTo(status)).as(status.name()).isFalse();
        }
    }

    @Test
    @DisplayName("REOPENED e alcancavel de RESOLVED e de CLOSED, e de nenhum outro")
    void reabrirSoDeResolvidoOuFechado() {
        assertThat(EnumSet.allOf(TicketStatus.class).stream().filter(s -> s.canTransitionTo(REOPENED)))
                .containsExactlyInAnyOrder(RESOLVED, CLOSED);
    }

    @Test
    @DisplayName("nada volta para OPEN")
    void nadaVoltaParaOpen() {
        // OPEN significa "ninguem olhou ainda". Um ticket que ja foi atendido e volta e
        // REOPENED — e a diferenca importa para o SLA de primeira resposta.
        assertThat(EnumSet.allOf(TicketStatus.class).stream().filter(s -> s.canTransitionTo(OPEN))).isEmpty();
    }

    @Test
    @DisplayName("null nunca e destino valido")
    void nullNaoEhDestino() {
        assertThat(OPEN.canTransitionTo(null)).isFalse();
    }
}
