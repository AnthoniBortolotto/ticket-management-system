package com.ticketsystem.team.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados para criar uma equipe.
 *
 * <p>Os tamanhos repetem as larguras da V2 (100 e 500), para a recusa sair como 400 com o
 * campo apontado, e nao como erro do banco.
 */
public record CreateTeamRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description) {
}
