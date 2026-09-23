package com.ticketsystem.team.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/** A pessoa ja participa desta equipe. Trocar o papel dela e outro endpoint. */
public class AlreadyTeamMemberException extends DomainException {

    public AlreadyTeamMemberException() {
        super(ProblemKind.CONFLICT, "Ja e membro da equipe",
                "Esta pessoa ja participa desta equipe. Para mudar o papel dela, altere o vinculo.");
    }
}
