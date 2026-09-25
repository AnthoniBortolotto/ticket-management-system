package com.ticketsystem.ticket.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Um comentario novo.
 *
 * <p>{@code internal} e obrigatorio, sem default, e o motivo e a direcao do erro: com default
 * {@code false}, um agente que esquecesse o campo ao escrever uma nota interna a publicaria
 * para o cliente. Obrigar a dizer e o que torna esse esquecimento um 400, e nao um
 * vazamento.
 */
public record CommentRequest(
        @NotBlank @Size(max = 5000) String body,
        @NotNull Boolean internal) {
}
