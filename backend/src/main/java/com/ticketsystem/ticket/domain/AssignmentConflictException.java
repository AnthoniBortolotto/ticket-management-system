package com.ticketsystem.ticket.domain;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * A operacao de atribuicao nao cabe no modo em que o ticket esta.
 *
 * <p>409: o pedido esta bem formado; e o estado do ticket que o impede.
 */
public class AssignmentConflictException extends DomainException {

    private AssignmentConflictException(String detail) {
        super(ProblemKind.CONFLICT, "Atribuicao incompativel com o modo do ticket", detail);
    }

    public static AssignmentConflictException notInTeamMode() {
        return new AssignmentConflictException(
                "O ticket esta em modo exclusivo. Devolva-o a equipe antes de mudar a atribuicao.");
    }

    public static AssignmentConflictException notInExclusiveMode() {
        return new AssignmentConflictException("O ticket ja esta com uma equipe.");
    }

    public static AssignmentConflictException alreadyInTeam() {
        return new AssignmentConflictException("O ticket ja esta nesta equipe.");
    }
}
