package com.ticketsystem.ticket.web.v1.dto;

import com.ticketsystem.ticket.TicketStatus;
import com.ticketsystem.ticket.domain.Ticket;
import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketPriority;
import java.time.Instant;

/**
 * Um ticket como a API o mostra.
 *
 * <p>Os campos de atribuicao estao todos no schema publicado, inclusive os do modo
 * exclusivo, que nesta fase sao sempre nulos — e por isso nao aparecem no JSON, que omite
 * nulos ({@code default-property-inclusion: non_null}). O contrato ja nasce com a forma que
 * a Fase 5 vai preencher: campo que aparece depois nao quebraria nada, mas campo que muda de
 * significado quebraria.
 *
 * <p>A {@code version} da entidade nao sai: o controle de concorrencia e do servidor, e
 * expo-la convidaria o cliente a usa-la sem o protocolo ({@code If-Match}) que a tornaria
 * segura.
 */
public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        TicketCategory category,
        Long requesterId,
        Long assignedTeamId,
        Long currentAssigneeId,
        Long exclusiveAssigneeId,
        Long originTeamId,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategory(),
                ticket.getRequesterId(),
                ticket.getAssignedTeamId(),
                ticket.getCurrentAssigneeId(),
                ticket.getExclusiveAssigneeId(),
                ticket.getOriginTeamId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
