package com.ticketsystem.ticket.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * O ticket nao existe — ou nao existe <em>para quem pergunta</em>.
 *
 * <p>Os dois casos saem com o mesmo corpo. Responder 403 a quem nao enxerga o ticket
 * confirmaria que o id existe, e varrer ids viraria um jeito de contar os chamados de outra
 * equipe.
 */
public class TicketNotFoundException extends DomainException {

    public TicketNotFoundException() {
        super(ProblemKind.NOT_FOUND, "Ticket nao encontrado",
                "Nao ha ticket com este id entre os que voce pode ver.");
    }
}
