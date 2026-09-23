package com.ticketsystem.team.web.v1.dto;

import com.ticketsystem.team.domain.TeamRole;
import jakarta.validation.constraints.NotNull;

/** O papel novo de alguem que ja participa da equipe. */
public record ChangeTeamRoleRequest(@NotNull TeamRole role) {
}
