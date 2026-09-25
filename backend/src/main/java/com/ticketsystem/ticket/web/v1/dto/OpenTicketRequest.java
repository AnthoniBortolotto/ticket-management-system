package com.ticketsystem.ticket.web.v1.dto;

import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O que o solicitante informa ao abrir um chamado.
 *
 * <p>Nao ha equipe aqui: ela sai da rota da categoria, e o solicitante nunca precisa conhecer
 * as equipes. Os tamanhos repetem as larguras da V5.
 */
public record OpenTicketRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotNull TicketCategory category,
        @NotNull TicketPriority priority) {
}
