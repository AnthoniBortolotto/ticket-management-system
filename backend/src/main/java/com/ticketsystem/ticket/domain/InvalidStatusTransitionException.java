package com.ticketsystem.ticket.domain;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;
import com.ticketsystem.ticket.TicketStatus;

/**
 * A transicao pedida nao existe no fluxo a partir do status atual.
 *
 * <p>409, e nao 400: o pedido esta bem formado; e o estado do ticket que o impede. Os dois
 * status vao no {@code detail} porque quem pede ja enxerga o ticket e o status dele — nao
 * ha o que vazar, e isso poupa uma consulta a quem integra.
 */
public class InvalidStatusTransitionException extends DomainException {

    public InvalidStatusTransitionException(TicketStatus de, TicketStatus para) {
        super(ProblemKind.CONFLICT, "Transicao de status invalida",
                "Um ticket em %s nao pode ir para %s.".formatted(de, para));
    }
}
