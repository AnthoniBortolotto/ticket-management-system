package com.ticketsystem.team.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * A equipe nao existe — ou nao existe <em>para quem pergunta</em>.
 *
 * <p>Os dois casos saem com o mesmo corpo, de proposito. Responder 403 a quem esta de fora
 * confirmaria que o id existe, e o texto abaixo e verdadeiro nos dois.
 */
public class TeamNotFoundException extends DomainException {

    public TeamNotFoundException() {
        super(ProblemKind.NOT_FOUND, "Equipe nao encontrada",
                "Nao ha equipe com este id entre as que voce pode ver.");
    }
}
