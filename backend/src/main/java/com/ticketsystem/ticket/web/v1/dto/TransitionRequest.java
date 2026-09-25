package com.ticketsystem.ticket.web.v1.dto;

import com.ticketsystem.ticket.TicketStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Para onde mover o ticket. So o destino: a origem e o status atual, e aceitar uma origem
 * vinda do cliente abriria a porta para ela divergir do que esta gravado.
 */
public record TransitionRequest(@NotNull TicketStatus to) {
}
