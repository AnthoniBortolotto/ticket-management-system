package com.ticketsystem.auth.service;

import com.ticketsystem.common.error.DomainException;
import com.ticketsystem.common.error.ProblemKind;

/**
 * A conta esta bloqueada por excesso de tentativas fracassadas.
 *
 * <p>Custo conhecido e aceito: responder 423 em vez de 401 revela que o e-mail tem conta.
 * Para chegar a essa resposta, porem, e preciso errar a senha daquela conta varias vezes
 * seguidas — e a pessoa legitima precisa saber por que nao consegue entrar. Fica
 * registrado no ADR 0003.
 *
 * <p>O {@code detail} nao diz quando o bloqueio acaba: isso ajuda quem esta forcando a
 * senha a cronometrar a proxima rajada.
 */
public class AccountLockedException extends DomainException {

    public AccountLockedException() {
        super(ProblemKind.LOCKED, "Conta bloqueada",
                "Conta temporariamente bloqueada por excesso de tentativas. Tente novamente mais tarde.");
    }
}
