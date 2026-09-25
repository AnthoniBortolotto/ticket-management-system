package com.ticketsystem.ticket.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * Quem pede enxerga o ticket, mas nao pode fazer isto com ele.
 *
 * <p>So chega aqui quem ja passou pela visibilidade: para os demais a resposta e 404.
 */
public class TicketActionForbiddenException extends DomainException {

    private TicketActionForbiddenException(String detail) {
        super(ProblemKind.FORBIDDEN, "Operacao nao permitida no ticket", detail);
    }

    public static TicketActionForbiddenException transition() {
        return new TicketActionForbiddenException(
                "Quem abriu o ticket pode confirmar o fechamento ou reabri-lo; o resto do fluxo e de quem o atende.");
    }

    public static TicketActionForbiddenException assignment() {
        return new TicketActionForbiddenException("Apenas quem atende o ticket muda o responsavel ou o devolve a equipe.");
    }

    public static TicketActionForbiddenException dispatch() {
        return new TicketActionForbiddenException(
                "Apenas o lider da equipe do ticket, ou um admin, o manda para o modo exclusivo ou para outra equipe.");
    }

    public static TicketActionForbiddenException internalNote() {
        return new TicketActionForbiddenException("Notas internas sao escritas apenas por quem atende o ticket.");
    }
}
