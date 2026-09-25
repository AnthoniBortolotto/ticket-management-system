package com.ticketsystem.ticket.web.v1.dto;

import jakarta.validation.constraints.NotNull;

/** A equipe que passa a ter o ticket. */
public record TransferRequest(@NotNull Long teamId) {
}
