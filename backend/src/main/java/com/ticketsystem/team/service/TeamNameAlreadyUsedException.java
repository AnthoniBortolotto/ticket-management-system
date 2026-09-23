package com.ticketsystem.team.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/** Ja existe equipe com este nome, sem distinguir maiusculas de minusculas. */
public class TeamNameAlreadyUsedException extends DomainException {

    public TeamNameAlreadyUsedException() {
        super(ProblemKind.CONFLICT, "Nome de equipe ja usado",
                "Ja existe uma equipe com este nome. Maiusculas e minusculas nao diferenciam nomes.");
    }
}
