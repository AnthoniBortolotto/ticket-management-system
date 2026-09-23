package com.ticketsystem.team.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * Solicitante nao participa de equipe.
 *
 * <p>Nao e so arrumacao: membro enxerga os tickets da equipe e os comentarios internos
 * deles, e o solicitante e justamente quem nunca pode ver comentario interno. Aceitar o
 * vinculo abriria esse acesso pela porta dos fundos.
 *
 * <p>409, e nao 400: o pedido esta bem formado; e o papel atual da pessoa que o impede.
 */
public class IneligibleTeamMemberException extends DomainException {

    public IneligibleTeamMemberException() {
        super(ProblemKind.CONFLICT, "Pessoa nao pode entrar em equipe",
                "Solicitantes nao participam de equipes; apenas agentes e administradores.");
    }
}
