package com.ticketsystem.ticket.domain;

import java.util.List;

/**
 * Uma pagina de tickets.
 *
 * <p>Tipo proprio, e nao o {@code Page} do Spring Data: o service nao importa
 * {@code org.springframework.data}, e e isso que deixa o armazenamento trocavel.
 *
 * @param page numero da pagina, a partir de zero
 */
public record TicketPage(List<Ticket> content, int page, int size, long totalElements) {

    public TicketPage {
        content = List.copyOf(content);
    }

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }
}
