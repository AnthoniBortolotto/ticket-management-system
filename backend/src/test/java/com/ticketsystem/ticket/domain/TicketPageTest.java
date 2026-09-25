package com.ticketsystem.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TicketPageTest {

    @ParameterizedTest(name = "{0} itens em paginas de {1}: {2} paginas")
    @CsvSource({"0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "40, 20, 2"})
    void totalDePaginas(long total, int tamanho, int paginas) {
        // As bordas sao onde a divisao inteira erra: um item a mais que o tamanho e uma pagina
        // a mais, e nenhum item e nenhuma pagina — nao uma pagina vazia.
        assertThat(new TicketPage(List.of(), 0, tamanho, total).totalPages()).isEqualTo(paginas);
    }
}
