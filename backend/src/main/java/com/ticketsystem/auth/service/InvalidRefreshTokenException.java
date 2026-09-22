package com.ticketsystem.auth.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * O refresh token nao serve: desconhecido, expirado, usado ou revogado.
 *
 * <p>Os quatro casos saem com a mesma resposta de proposito. Dizer "este token ja foi
 * usado" confirmaria a quem tem uma copia roubada que ela era valida.
 */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super(ProblemKind.UNAUTHORIZED, "Sessao invalida",
                "A sessao expirou ou foi encerrada. Entre novamente.");
    }
}
