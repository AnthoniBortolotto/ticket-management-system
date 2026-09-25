package com.ticketsystem.ticket.web.v1.dto;

import jakarta.validation.constraints.NotNull;

/** Quem passa a atuar no ticket — como responsavel atual ou exclusivo. */
public record AssigneeRequest(@NotNull Long assigneeId) {
}
