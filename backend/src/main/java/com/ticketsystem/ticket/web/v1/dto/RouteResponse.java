package com.ticketsystem.ticket.web.v1.dto;

import com.ticketsystem.ticket.domain.TicketCategory;
import com.ticketsystem.ticket.domain.TicketRoute;

/** Uma categoria e a equipe que a atende. */
public record RouteResponse(TicketCategory category, Long teamId) {

    public static RouteResponse from(TicketRoute rota) {
        return new RouteResponse(rota.getCategory(), rota.getTeamId());
    }
}
