package com.ticketsystem.team.web.v1.dto;

import jakarta.validation.constraints.NotNull;

/** Quem entra na equipe. O papel nao vem aqui: todo mundo entra como {@code MEMBER}. */
public record AddMemberRequest(@NotNull Long userId) {
}
