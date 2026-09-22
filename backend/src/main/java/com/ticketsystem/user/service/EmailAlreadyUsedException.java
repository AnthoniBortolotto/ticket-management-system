package com.ticketsystem.user.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * Ja existe conta com este e-mail.
 *
 * <p>O e-mail nao entra no {@code detail}: a resposta de criacao de usuario e visivel para
 * quem chamou, e repetir o endereco tentado transformaria o endpoint num confirmador de
 * contas. Quem cria usuario e admin e ja sabe o que digitou.
 */
public class EmailAlreadyUsedException extends DomainException {

    public EmailAlreadyUsedException() {
        super(ProblemKind.CONFLICT, "E-mail ja cadastrado", "Ja existe um usuario com este e-mail.");
    }
}
