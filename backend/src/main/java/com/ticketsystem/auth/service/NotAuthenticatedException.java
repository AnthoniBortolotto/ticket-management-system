package com.ticketsystem.auth.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * Nao da para dizer quem esta pedindo.
 *
 * <p>Nas rotas protegidas a cadeia de filtros ja recusa antes, entao isto so dispara se
 * alguem chamar {@code AuthFacade.currentUser()} numa rota publica, ou se um token chegar
 * com identidade que nao saiu do nosso login. Nos dois casos a resposta e 401 — falhar
 * fechado —, e o {@code detail} e o mesmo do 401 da cadeia de filtros, para nao descrever
 * o que exatamente estava errado.
 */
public class NotAuthenticatedException extends DomainException {

    public NotAuthenticatedException() {
        super(ProblemKind.UNAUTHORIZED, "Nao autenticado", "Credenciais ausentes ou invalidas.");
    }
}
