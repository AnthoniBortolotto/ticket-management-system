package com.ticketsystem.ticket.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * A rota aponta para uma equipe que nao existe.
 *
 * <p>400, e nao 404: o recurso da URL — a categoria — existe; e um campo do corpo que esta
 * errado. Checado aqui, e nao deixado a chave estrangeira, para sair como erro de entrada e
 * nao como violacao de constraint.
 */
public class RouteTeamNotFoundException extends DomainException {

    public RouteTeamNotFoundException() {
        super(ProblemKind.INVALID, "Equipe inexistente", "A equipe informada para a rota nao existe.");
    }
}
