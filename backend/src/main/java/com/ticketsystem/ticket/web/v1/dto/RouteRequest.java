package com.ticketsystem.ticket.web.v1.dto;

import jakarta.validation.constraints.NotNull;

/** A equipe que passa a receber os tickets da categoria. */
public record RouteRequest(@NotNull Long teamId) {
}
