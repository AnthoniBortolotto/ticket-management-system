package com.ticketsystem.ticket.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * Um campo do pedido aponta para uma equipe que nao existe — a rota de uma categoria ou o
 * destino de uma transferencia.
 *
 * <p>400, e nao 404: o recurso da URL existe; e um campo do corpo que esta errado. Checado
 * aqui, e nao deixado a chave estrangeira, para sair como erro de entrada e nao como violacao
 * de constraint.
 */
public class UnknownTeamException extends DomainException {

    public UnknownTeamException() {
        super(ProblemKind.INVALID, "Equipe inexistente", "A equipe informada nao existe.");
    }
}
