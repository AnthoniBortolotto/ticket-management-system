package com.ticketsystem.auth.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * E-mail ou senha nao conferem.
 *
 * <p>Uma excecao so para os dois casos, com o mesmo texto, de proposito: "este e-mail nao
 * existe" e "a senha esta errada" como respostas diferentes transformariam o login num
 * verificador de contas.
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super(ProblemKind.UNAUTHORIZED, "Credenciais invalidas", "E-mail ou senha incorretos.");
    }
}
