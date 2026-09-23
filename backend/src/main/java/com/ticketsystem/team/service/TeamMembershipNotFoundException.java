package com.ticketsystem.team.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * A pessoa nao participa desta equipe.
 *
 * <p>So chega aqui quem ja pode gerenciar a equipe: para os demais, a recusa acontece
 * antes, e a resposta nao diz quem e ou nao e membro.
 */
public class TeamMembershipNotFoundException extends DomainException {

    public TeamMembershipNotFoundException() {
        super(ProblemKind.NOT_FOUND, "Vinculo nao encontrado", "Esta pessoa nao participa desta equipe.");
    }
}
